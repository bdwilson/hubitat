// Scrypted Connect — Hubitat bridge (Scrypted Script)
// Version: 0.1.0
//
// Install: Scrypted -> Plugins -> Scripts -> "+" -> paste this file -> SAVE
// (Save, not Run: Scrypted only stores a script on Save). The console prints
// the Bridge URL and Bridge Token to paste into the Hubitat "Scrypted Connect" app.
// Nothing in this file needs editing; Hubitat pushes its settings to the bridge.
//
// What it does
//   * Detections -> Hubitat. Listens to every camera's ObjectDetector events
//     (verified Scrypted NVR detections; raw "motion" and internal "clip" frames
//     are ignored). The first matching detection opens a session ("active");
//     the session closes ("inactive") after `gapSeconds` with no further matches.
//   * Hubitat -> Scrypted notification muting. Muting a camera adds it to
//     "Custom Notifications" on every Scrypted NVR notifier (phones, Mac, browser)
//     with no notification types selected. Recording and detection keep running.
//     Unmuting restores exactly what was there before. Mutes can be timed or
//     indefinite and survive a Scrypted restart.
//
// HTTP API (all under the Bridge URL; every call needs
// "Authorization: Bearer <token>" except GET on the bare URL):
//   GET  status                 -> bridge + camera state
//   POST config  {callback, classes, minScore, gapSeconds}
//   POST mute    {cameras: [key|name, ...] | "all", seconds?}   (no seconds = until unmuted)
//   POST unmute  {cameras: [key|name, ...] | "all"}

const VERSION = '0.1.0';
const IGNORED_CLASSES = new Set(['motion', 'clip']);
// Camera-native (e.g. Reolink) class names folded into Scrypted NVR's names.
const CLASS_ALIASES: Record<string, string> = { people: 'person', dog_cat: 'animal', car: 'vehicle' };
const DEFAULT_CONFIG = { callback: '', classes: ['person', 'vehicle', 'animal', 'package'], minScore: 0.6, gapSeconds: 30 };
const CAMERA_RESCAN_MS = 60_000;
const MAX_TIMER_MS = 2_000_000_000; // setTimeout limit is ~24.8 days

const g: any = globalThis as any;
try { g.__scryptedConnectBridge?.dispose(); } catch { }

// ---------- persistence (Script device storage) ----------
const store = {
    get<T>(key: string, def: T): T {
        try { const v = localStorage.getItem(key); return v == null ? def : JSON.parse(v); } catch { return def; }
    },
    set(key: string, value: any) {
        if (value === undefined) localStorage.removeItem(key);
        else localStorage.setItem(key, JSON.stringify(value));
    },
};

let token: string = store.get('token', '');
if (!token) {
    token = require('crypto').randomBytes(16).toString('hex');
    store.set('token', token);
}

type Config = typeof DEFAULT_CONFIG;
const getConfig = (): Config => ({ ...DEFAULT_CONFIG, ...store.get<Partial<Config>>('config', {}) });

// ---------- cameras ----------
interface Cam { id: string; key: string; name: string; dev: any; hasDetector: boolean; }
let cameras = new Map<string, Cam>();   // key -> Cam

// Scrypted device ids can change when a camera is re-added, so cameras are keyed
// by plugin + native id, and API callers may also address them by name.
function cameraKey(d: any): string {
    return d.pluginId && d.nativeId ? `${d.pluginId}:${d.nativeId}` : `name:${d.name}`;
}

function scanCameras(): Map<string, Cam> {
    const found = new Map<string, Cam>();
    for (const id of Object.keys(systemManager.getSystemState())) {
        const d: any = systemManager.getDeviceById(id);
        if (!d?.interfaces?.includes(ScryptedInterface.VideoCamera)) continue;
        const key = cameraKey(d);
        found.set(key, { id, key, name: d.name, dev: d, hasDetector: d.interfaces.includes(ScryptedInterface.ObjectDetector) });
    }
    return found;
}

function resolveCamera(ref: string): Cam | undefined {
    return cameras.get(ref) || [...cameras.values()].find(c => c.name === ref);
}

// ---------- detection sessions ----------
interface Session { cam: Cam; startMs: number; lastMs: number; classes: Set<string>; labels: Set<string>; maxScore: number; timer?: any; }
const sessions = new Map<string, Session>();   // camera key -> open session
const detectorListeners = new Map<string, any>(); // device id -> listener registration

function onDetections(cam: Cam, data: any) {
    const cfg = getConfig();
    const wanted = new Set(cfg.classes);
    const hits = (data?.detections || [])
        .map((x: any) => ({ ...x, className: CLASS_ALIASES[x.className] || x.className }))
        .filter((x: any) => x.className && !IGNORED_CLASSES.has(x.className)
            && wanted.has(x.className) && (x.score ?? 1) >= cfg.minScore);
    if (!hits.length) return;

    const now = Date.now();
    let s = sessions.get(cam.key);
    const isNew = !s;
    if (!s) {
        s = { cam, startMs: now, lastMs: now, classes: new Set(), labels: new Set(), maxScore: 0 };
        sessions.set(cam.key, s);
    }
    const classCount = s.classes.size;
    s.lastMs = now;
    for (const x of hits) {
        s.classes.add(x.className);
        if (x.label) s.labels.add(String(x.label));
        s.maxScore = Math.max(s.maxScore, x.score ?? 1);
    }
    clearTimeout(s.timer);
    s.timer = setTimeout(() => endSession(cam.key), cfg.gapSeconds * 1000);

    if (isNew || s.classes.size > classCount)
        postToHubitat({ type: 'detection', state: 'active', update: !isNew, ...sessionPayload(s) });
}

function endSession(key: string) {
    const s = sessions.get(key);
    if (!s) return;
    clearTimeout(s.timer);
    sessions.delete(key);
    postToHubitat({ type: 'detection', state: 'inactive', ...sessionPayload(s), durationSec: Math.round((s.lastMs - s.startMs) / 1000) });
}

function sessionPayload(s: Session) {
    return {
        camera: { key: s.cam.key, name: s.cam.name },
        classes: [...s.classes], labels: [...s.labels],
        maxScore: Math.round(s.maxScore * 100) / 100,
        startedAt: s.startMs,
    };
}

function refreshCameras() {
    cameras = scanCameras();
    const liveIds = new Set([...cameras.values()].map(c => c.id));
    for (const [id, reg] of detectorListeners) {
        if (!liveIds.has(id)) { try { reg.removeListener(); } catch { } detectorListeners.delete(id); }
    }
    for (const cam of cameras.values()) {
        if (!cam.hasDetector || detectorListeners.has(cam.id)) continue;
        // listenDevice (not systemManager.listen) is what receives the NVR's detection frames.
        detectorListeners.set(cam.id, systemManager.listenDevice(cam.id, ScryptedInterface.ObjectDetector,
            (_src: any, _details: any, data: any) => {
                const current = [...cameras.values()].find(c => c.id === cam.id);
                if (current) onDetections(current, data);
            }));
    }
}

// ---------- Hubitat callback ----------
let postChain: Promise<any> = Promise.resolve();   // keeps active/inactive in order
function postToHubitat(body: any) {
    const url = getConfig().callback;
    if (!url) return;
    postChain = postChain.then(async () => {
        const ctrl = new AbortController();
        const t = setTimeout(() => ctrl.abort(), 5000);
        try {
            const r = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body), signal: ctrl.signal });
            if (!r.ok) console.warn(`Hubitat returned HTTP ${r.status} for ${body.type}`);
        } catch (e: any) {
            console.warn(`Hubitat post failed (${body.type}): ${e?.message || e}`);
        } finally { clearTimeout(t); }
    });
}

// ---------- notification muting ----------
// mutes[cameraKey] = { name, camId, until: epoch ms | null (indefinite),
//                      originals: { [notifierId]: { inCustom, value } } }
interface MuteRecord { name: string; camId: string; until: number | null; originals: Record<string, { inCustom: boolean; value: any }>; }
const getMutes = () => store.get<Record<string, MuteRecord>>('mutes', {});
const muteTimers = new Map<string, any>();

let opChain: Promise<any> = Promise.resolve();   // serialize notifier setting writes
function serial<T>(fn: () => Promise<T>): Promise<T> {
    const p = opChain.then(fn, fn);
    opChain = p.catch(() => { });
    return p;
}

function nvrNotifiers(): any[] {
    return Object.keys(systemManager.getSystemState())
        .map(id => systemManager.getDeviceById(id) as any)
        .filter(d => d?.interfaces?.includes(ScryptedInterface.Notifier)
            && d.interfaces.includes(ScryptedInterface.Settings)
            && d.interfaces.includes('@scrypted/nvr:Notifier'));
}

async function settingValue(dev: any, key: string): Promise<any> {
    const settings = await dev.getSettings();
    return settings.find((s: any) => s.key === key)?.value;
}

async function silenceOnNotifier(n: any, camId: string, rec: MuteRecord) {
    const custom: string[] = (await settingValue(n, 'customCameras')) || [];
    const typesKey = `custom-notifications-${camId}`;
    if (!rec.originals[n.id]) {
        rec.originals[n.id] = { inCustom: custom.includes(camId), value: await settingValue(n, typesKey) };
    }
    if (!custom.includes(camId)) await n.putSetting('customCameras', [...custom, camId]);
    await n.putSetting(typesKey, []);
}

function muteCamera(cam: Cam, seconds?: number | null) {
    return serial(async () => {
        const mutes = getMutes();
        const until = seconds && seconds > 0 ? Date.now() + seconds * 1000 : null;
        let rec = mutes[cam.key];
        if (rec) {
            // already muted: indefinite wins, otherwise extend to the later end time
            rec.until = rec.until === null || until === null ? null : Math.max(rec.until, until);
        } else {
            rec = { name: cam.name, camId: cam.id, until, originals: {} };
        }
        mutes[cam.key] = rec;
        // Record originals before changing anything, so a crash mid-way can still be undone.
        for (const n of nvrNotifiers()) {
            try {
                await silenceOnNotifier(n, rec.camId, rec);
                store.set('mutes', mutes);
            } catch (e: any) { console.warn(`mute ${cam.name} on ${n.name} failed: ${e?.message || e}`); }
        }
        store.set('mutes', mutes);
        scheduleUnmute(cam.key);
        console.log(`Muted notifications for ${cam.name} ${rec.until ? 'until ' + new Date(rec.until).toLocaleString() : 'until unmuted'}`);
        postToHubitat({ type: 'mute', camera: { key: cam.key, name: cam.name }, muted: true, until: rec.until });
    });
}

function unmuteCamera(key: string) {
    return serial(async () => {
        const mutes = getMutes();
        const rec = mutes[key];
        clearTimeout(muteTimers.get(key));
        muteTimers.delete(key);
        if (!rec) return;
        const typesKey = `custom-notifications-${rec.camId}`;
        for (const [nid, orig] of Object.entries(rec.originals)) {
            const n: any = systemManager.getDeviceById(nid);
            if (!n) continue;
            try {
                await n.putSetting(typesKey, orig.value);
                if (!orig.inCustom) {
                    const custom: string[] = (await settingValue(n, 'customCameras')) || [];
                    await n.putSetting('customCameras', custom.filter(id => id !== rec.camId));
                }
            } catch (e: any) { console.warn(`unmute ${rec.name} on ${n.name} failed: ${e?.message || e}`); }
        }
        delete mutes[key];
        store.set('mutes', mutes);
        console.log(`Restored notifications for ${rec.name}`);
        postToHubitat({ type: 'mute', camera: { key, name: rec.name }, muted: false, until: null });
    });
}

function scheduleUnmute(key: string) {
    clearTimeout(muteTimers.get(key));
    muteTimers.delete(key);
    const rec = getMutes()[key];
    if (!rec || rec.until === null) return;
    const delay = rec.until - Date.now();
    if (delay <= 0) { unmuteCamera(key); return; }
    muteTimers.set(key, setTimeout(() => {
        const r = getMutes()[key];
        if (r && r.until !== null && r.until > Date.now()) scheduleUnmute(key);   // long mute: re-arm
        else unmuteCamera(key);
    }, Math.min(delay, MAX_TIMER_MS)));
}

// ---------- status ----------
function status() {
    const mutes = getMutes();
    const cfg = getConfig();
    return {
        version: VERSION,
        callbackConfigured: !!cfg.callback,
        config: { classes: cfg.classes, minScore: cfg.minScore, gapSeconds: cfg.gapSeconds },
        notifiers: nvrNotifiers().map(n => ({ id: n.id, name: n.name })),
        cameras: [...cameras.values()].map(c => ({
            key: c.key, name: c.name, id: c.id, hasDetector: c.hasDetector,
            active: sessions.has(c.key), classes: sessions.has(c.key) ? [...sessions.get(c.key)!.classes] : [],
            muted: !!mutes[c.key], until: mutes[c.key]?.until ?? null,
        })),
    };
}

// ---------- HTTP ----------
function tokenOk(request: any): boolean {
    const h = request.headers || {};
    const auth = String(h.authorization || h.Authorization || '');
    const supplied = auth.startsWith('Bearer ') ? auth.slice(7).trim() : '';
    const a = Buffer.from(supplied), b = Buffer.from(token);
    return a.length === b.length && require('crypto').timingSafeEqual(a, b);
}

function parseBody(request: any): any {
    if (!request.body) return {};
    if (typeof request.body === 'object') return request.body;
    try { return JSON.parse(String(request.body)); } catch { return {}; }
}

function camerasFrom(ref: any): Cam[] {
    if (ref === 'all') return [...cameras.values()];
    const refs: string[] = Array.isArray(ref) ? ref.map(String) : ref ? [String(ref)] : [];
    return refs.map(resolveCamera).filter((c): c is Cam => !!c);
}

class ScryptedConnectBridge extends ScryptedDeviceBase {
    async onRequest(request: any, response: any) {
        const send = (code: number, obj: any) =>
            response.send(JSON.stringify(obj), { code, headers: { 'Content-Type': 'application/json' } });
        try {
            const path = String(request.url || '').split('?')[0];
            const route = (path.split('/public/')[1] || '').replace(/\/+$/, '');
            if (!route) return send(200, { scryptedConnect: VERSION });
            if (!tokenOk(request)) return send(401, { error: 'bad or missing bearer token' });

            const body = parseBody(request);
            switch (route) {
                case 'status':
                    return send(200, status());
                case 'config': {
                    const cur = getConfig();
                    const next: Config = {
                        callback: typeof body.callback === 'string' && /^https?:\/\//.test(body.callback) ? body.callback : cur.callback,
                        classes: Array.isArray(body.classes) && body.classes.length ? body.classes.map(String) : cur.classes,
                        minScore: Number.isFinite(+body.minScore) && body.minScore !== null && body.minScore !== '' ? Math.min(1, Math.max(0, +body.minScore)) : cur.minScore,
                        gapSeconds: Number.isFinite(+body.gapSeconds) && +body.gapSeconds >= 5 ? Math.round(+body.gapSeconds) : cur.gapSeconds,
                    };
                    store.set('config', next);
                    return send(200, status());
                }
                case 'mute': {
                    const cams = camerasFrom(body.cameras);
                    if (!cams.length) return send(404, { error: 'no matching cameras', cameras: body.cameras });
                    const seconds = body.seconds === undefined || body.seconds === null ? null : Number(body.seconds);
                    for (const c of cams) await muteCamera(c, seconds);
                    return send(200, status());
                }
                case 'unmute': {
                    const keys = body.cameras === 'all' ? Object.keys(getMutes()) : camerasFrom(body.cameras).map(c => c.key);
                    for (const k of keys) await unmuteCamera(k);
                    return send(200, status());
                }
                default:
                    return send(404, { error: `unknown route ${route}` });
            }
        } catch (e: any) {
            console.error('request failed', e);
            return send(500, { error: String(e?.message || e) });
        }
    }
}

// ---------- startup / shutdown ----------
refreshCameras();
const rescan = setInterval(refreshCameras, CAMERA_RESCAN_MS);
for (const key of Object.keys(getMutes())) scheduleUnmute(key);   // resume or expire mutes

g.__scryptedConnectBridge = {
    dispose() {
        clearInterval(rescan);
        for (const reg of detectorListeners.values()) { try { reg.removeListener(); } catch { } }
        detectorListeners.clear();
        for (const key of [...sessions.keys()]) endSession(key);   // don't leave Hubitat sensors stuck active
        for (const t of muteTimers.values()) clearTimeout(t);
        muteTimers.clear();
    },
};

(async () => {
    const url = await endpointManager.getLocalEndpoint(device.nativeId, { public: true, insecure: true });
    console.log(`Scrypted Connect bridge ${VERSION} running: ${cameras.size} cameras, ${nvrNotifiers().length} NVR notifiers, ${Object.keys(getMutes()).length} active mutes`);
    console.log(`Bridge URL:   ${url}`);
    console.log(`Bridge Token: ${token}`);
    postToHubitat({ type: 'hello', status: status() });
})();

export default ScryptedConnectBridge;
