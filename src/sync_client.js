/* ============================================================
   OFFICE DREAMS — sync client (pure logic, no DOM, no fetch)
   Keeps the local prediction and the action log in step with the
   authoritative server state. Used by the browser and by the tests.
   ============================================================ */
function createSyncClient(S, EN) {
  const c = {
    S,
    seq: 0,
    queue: [], // [[dTicks, action, ...args]] not yet sent
    lastTk: S.tk, // tick of the last queued action (or of the last sync point)
    inflight: null,
    stats: { adopts: 0, same: 0, stale: 0, replayFails: 0 },
  };
  // run a player action locally (prediction) and log it for the server
  c.act = function (type, ...args) {
    const r = EN.applyAction(c.S, [type].concat(args));
    if (r.ok) {
      c.queue.push([Math.round(c.S.tk - c.lastTk), type].concat(args));
      c.lastTk = c.S.tk;
    }
    return r;
  };
  c.pending = function () {
    return c.queue.length;
  };
  // build the request body (null while a request is in flight)
  c.prepare = function () {
    if (c.inflight) return null;
    const tail = Math.round(c.S.tk - c.lastTk);
    const body = { seq: c.seq, batch: c.queue, tail, ck: EN.stateHash(c.S) };
    c.inflight = { batch: c.queue, tail, sentTk: c.S.tk };
    c.queue = [];
    c.lastTk = c.S.tk;
    return body;
  };
  // the request never produced a usable answer: put everything back
  c.fail = function () {
    const inf = c.inflight;
    if (!inf) return;
    c.inflight = null;
    if (c.queue.length) c.queue[0][0] += inf.tail;
    else c.lastTk = inf.sentTk - inf.tail;
    c.queue = inf.batch.concat(c.queue);
  };
  function adopt(stateObj) {
    const keep = { sel: c.S.sel, settings: c.S.settings };
    const ns = EN.deserialize(stateObj);
    ns.sel = keep.sel || ns.sel;
    ns.settings = keep.settings || ns.settings;
    return ns;
  }
  // server answer. Returns {state: true} when c.S was replaced.
  c.apply = function (resp) {
    const inf = c.inflight;
    c.inflight = null;
    if (!resp || typeof resp !== 'object') return { state: false };
    if (resp.seq !== undefined) c.seq = resp.seq;
    if (resp.ok === false) {
      if (resp.state) {
        // stale / clock violation: the server wins, pending actions are dropped
        c.S = adopt(resp.state);
        c.queue = [];
        c.lastTk = c.S.tk;
        c.stats.stale++;
        return { state: true, dropped: true, err: resp.err };
      }
      if (inf) { c.inflight = inf; c.fail(); }
      return { state: false, err: resp.err };
    }
    if (resp.state) {
      const ns = adopt(resp.state);
      const flightTail = Math.round(c.S.tk - c.lastTk);
      c.stats.replayFails += EN.replay(ns, c.queue, flightTail);
      c.S = ns;
      c.lastTk = ns.tk - flightTail;
      c.stats.adopts++;
      return { state: true };
    }
    c.stats.same++;
    return { state: false };
  };
  return c;
}

if (typeof module !== 'undefined') module.exports = { createSyncClient };
