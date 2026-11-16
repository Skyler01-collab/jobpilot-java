// Service worker: talks to the local JobPilot server and runs the apply queue.
const DEFAULTS = { server: 'http://127.0.0.1:8765', token: '', autoClick: false, attachResume: true, ai: true };

async function cfg() {
  return { ...DEFAULTS, ...(await chrome.storage.local.get(Object.keys(DEFAULTS))) };
}

function headers(c, withBody) {
  return { 'X-JobPilot': 'ext', 'X-JobPilot-Token': c.token, ...(withBody ? { 'Content-Type': 'application/json' } : {}) };
}

async function request(path, method, body) {
  const c = await cfg();
  if (!c.token) throw new Error('Not connected. Click the JobPilot icon and enter your token.');
  let r;
  try {
    r = await fetch(c.server.replace(/\/$/, '') + '/api' + path, {
      method: method || 'GET', headers: headers(c, !!body), body: body ? JSON.stringify(body) : undefined
    });
  } catch (e) {
    throw new Error('JobPilot is not running at ' + c.server);
  }
  if (!r.ok) {
    let msg = r.statusText;
    try { msg = (await r.json()).error || msg; } catch (e) { /* not json */ }
    throw new Error(msg);
  }
  return r;
}

async function call(path, method, body) {
  const r = await request(path, method, body);
  return (r.headers.get('content-type') || '').includes('json') ? r.json() : r.text();
}

async function callPdf(jobId) {
  const r = await request(`/jobs/${jobId}/resume.pdf`);
  const m = /filename="?([^";]+)"?/.exec(r.headers.get('content-disposition') || '');
  const buf = new Uint8Array(await r.arrayBuffer());
  let s = '';
  for (let i = 0; i < buf.length; i += 0x8000) s += String.fromCharCode.apply(null, buf.subarray(i, i + 0x8000));
  return { name: m ? m[1] : 'resume.pdf', base64: btoa(s) };
}

/* ----- pending answers (what you typed on the form, remembered when you press Applied) ----- */
async function addPending(key, answers) {
  const { pending = {} } = await chrome.storage.local.get('pending');
  pending[key] = { ...(pending[key] || {}), ...answers };
  await chrome.storage.local.set({ pending });
}
async function dropPending(key) {
  const { pending = {} } = await chrome.storage.local.get('pending');
  delete pending[key];
  await chrome.storage.local.set({ pending });
}
async function flushPending(key) {
  const { pending = {} } = await chrome.storage.local.get('pending');
  const answers = pending[key] || {};
  await dropPending(key);
  let n = 0;
  for (const [q, v] of Object.entries(answers)) {
    try { await call('/answers', 'PUT', { question: q, answer: v }); n++; } catch (e) { /* keep going */ }
  }
  return n;
}
async function pendKey(sender) {
  const { queue } = await chrome.storage.local.get('queue');
  const tabId = sender.tab && sender.tab.id;
  return queue && queue.active && tabId === queue.tabId ? 'q' : 't' + tabId;
}

/* ----- queue ----- */
async function startQueue() {
  const jobs = await call('/jobs');
  const q = jobs.filter(j => j.status === 'QUEUED' && j.url);
  if (!q.length) throw new Error('No queued jobs yet. Open a job and use "Tailor resume & queue".');
  const tab = await chrome.tabs.create({ url: q[0].url });
  await dropPending('q');
  await chrome.storage.local.set({
    queue: { active: true, tabId: tab.id, i: 0, jobs: q.map(j => ({ id: j.id, title: j.title, company: j.company, url: j.url })) }
  });
  return q.length;
}

async function advance(markApplied) {
  const { queue } = await chrome.storage.local.get('queue');
  if (!queue || !queue.active) throw new Error('The queue is not running');
  const job = queue.jobs[queue.i];
  if (markApplied) {
    await call(`/jobs/${job.id}/status?value=APPLIED`, 'PATCH');
    await flushPending('q');
  } else {
    await dropPending('q');
  }
  queue.i++;
  if (queue.i >= queue.jobs.length) {
    queue.active = false;
    await chrome.storage.local.set({ queue });
    return { finished: true };
  }
  await chrome.storage.local.set({ queue });
  await chrome.tabs.update(queue.tabId, { url: queue.jobs[queue.i].url });
  return { finished: false };
}

// Apply buttons often open a new tab; keep following the queue into it.
chrome.tabs.onCreated.addListener(async tab => {
  const { queue } = await chrome.storage.local.get('queue');
  if (queue && queue.active && tab.openerTabId === queue.tabId) {
    queue.tabId = tab.id;
    await chrome.storage.local.set({ queue });
  }
});

async function handle(m, sender) {
  switch (m.type) {
    case 'api': return call(m.path, m.method, m.body);
    case 'capture': return call('/ext/capture', 'POST', { jobs: m.jobs });
    case 'cover': return call(`/jobs/${m.jobId}/cover-letter.txt`);
    case 'pdf': return callPdf(m.jobId);
    case 'opts': {
      const c = await cfg();
      return { server: c.server, autoClick: c.autoClick, attachResume: c.attachResume, ai: c.ai };
    }
    case 'fill.all':
      try { await chrome.tabs.sendMessage(sender.tab.id, { type: 'jp.fill', opts: { jobId: m.jobId } }); } catch (e) { /* no listener */ }
      return true;
    case 'answers.pending':
      await addPending(await pendKey(sender), m.answers || {});
      return true;
    case 'applied': {
      if (m.jobId) await call(`/jobs/${m.jobId}/status?value=APPLIED`, 'PATCH');
      return flushPending(await pendKey(sender));
    }
    case 'queue.state': {
      const { queue } = await chrome.storage.local.get('queue');
      if (!queue) return null;
      const active = !!queue.active && !!sender.tab && sender.tab.id === queue.tabId;
      return { active, index: queue.i, total: queue.jobs.length, job: queue.jobs[queue.i] || null };
    }
    case 'queue.peek': {
      const { queue } = await chrome.storage.local.get('queue');
      return queue && queue.active ? queue.jobs[queue.i] || null : null;
    }
    case 'pair': {
      // Only the JobPilot page itself (loopback address) may set the token.
      const origin = sender.url ? new URL(sender.url).origin : '';
      if (!/^http:\/\/(127\.0\.0\.1|localhost):\d+$/.test(origin)) throw new Error('Pairing is only allowed from the JobPilot page');
      await chrome.storage.local.set({ server: origin, token: m.token });
      await call('/settings'); // proves the token works
      return true;
    }
    case 'queue.start': return startQueue();
    case 'queue.next': return advance(true);
    case 'queue.skip': return advance(false);
    case 'queue.stop': {
      const { queue } = await chrome.storage.local.get('queue');
      if (queue) { queue.active = false; await chrome.storage.local.set({ queue }); }
      return true;
    }
    default: throw new Error('Unknown message: ' + m.type);
  }
}

chrome.runtime.onMessage.addListener((m, sender, send) => {
  handle(m, sender).then(data => send({ ok: true, data })).catch(e => send({ ok: false, error: e.message }));
  return true;
});
