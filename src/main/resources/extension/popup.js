const $ = id => document.getElementById(id);
const DEFAULTS = { server: 'http://127.0.0.1:8765', token: '', autoClick: false, attachResume: true, ai: true };

const msg = (t, err) => { $('msg').textContent = t; $('msg').className = err ? 'err' : ''; };
const bg = (type, payload = {}) => new Promise(res =>
  chrome.runtime.sendMessage({ type, ...payload }, r => res(chrome.runtime.lastError ? { ok: false, error: chrome.runtime.lastError.message } : r)));
const cfg = async () => ({ ...DEFAULTS, ...(await chrome.storage.local.get(Object.keys(DEFAULTS))) });
const activeTab = async () => (await chrome.tabs.query({ active: true, currentWindow: true }))[0];

async function refresh() {
  const c = await cfg();
  $('server').value = c.server;
  $('token').value = c.token;
  $('autoClick').checked = c.autoClick;
  $('attachResume').checked = c.attachResume;
  $('ai').checked = c.ai;
  if (!c.token) { setConnected(false); return; }
  const r = await bg('api', { path: '/settings' });
  setConnected(r && r.ok, r && !r.ok ? r.error : '');
  if (r && r.ok) {
    const jobs = await bg('api', { path: '/jobs' });
    const n = jobs.ok ? jobs.data.filter(j => j.status === 'QUEUED' && j.url).length : 0;
    $('status').textContent = `Connected · ${n} job${n === 1 ? '' : 's'} queued`;
  }
}
function setConnected(ok, err) {
  $('connectBox').hidden = ok;
  $('main').hidden = !ok;
  if (!ok) { $('status').textContent = 'Not connected'; if (err) msg(err, true); }
}

$('connect').onclick = async () => {
  const server = $('server').value.trim().replace(/\/$/, '') || DEFAULTS.server;
  const token = $('token').value.trim();
  if (!token) return msg('Paste the token from JobPilot > Settings first.', true);
  await chrome.storage.local.set({ server, token });
  msg('Connecting…');
  const r = await bg('api', { path: '/settings' });
  if (r && r.ok) { msg('Connected.'); refresh(); } else msg((r && r.error) || 'Could not connect', true);
};
$('change').onclick = () => { $('connectBox').hidden = false; $('main').hidden = true; };

['autoClick', 'attachResume', 'ai'].forEach(k => ($(k).onchange = () => chrome.storage.local.set({ [k]: $(k).checked })));

/* runs inside the page */
function readPage() {
  const h1 = document.querySelector('h1');
  const sel = String(window.getSelection() || '').trim();
  return { title: (h1 && h1.innerText.trim()) || document.title, url: location.href, host: location.hostname,
    text: sel.length > 200 ? sel : (document.body.innerText || '').slice(0, 20000) };
}

$('save').onclick = async () => {
  try {
    const tab = await activeTab();
    const [{ result }] = await chrome.scripting.executeScript({ target: { tabId: tab.id }, func: readPage });
    const r = await bg('api', { path: '/jobs', method: 'POST', body: { title: result.title, company: result.host.replace(/^www\./, ''), url: result.url, description: result.text } });
    if (!r.ok) return msg(r.error, true);
    msg(`Saved. Match score: ${r.data.score}. Fix the company name in JobPilot if needed.`);
  } catch (e) { msg(e.message, true); }
};

$('fill').onclick = async () => {
  try {
    const tab = await activeTab();
    const peek = await bg('queue.peek');
    const jobId = peek.ok && peek.data ? peek.data.id : undefined;
    await chrome.scripting.executeScript({ target: { tabId: tab.id, allFrames: true }, files: ['common.js'] });
    const res = await chrome.scripting.executeScript({ target: { tabId: tab.id, allFrames: true }, func: o => window.__jp.fillPage(o), args: [{ jobId }] });
    const sum = res.reduce((a, r) => ({ g: a.g + ((r.result || {}).green || 0), a: a.a + ((r.result || {}).amber || 0), f: a.f + ((r.result || {}).files || 0), e: a.e || (r.result || {}).error }), { g: 0, a: 0, f: 0, e: '' });
    if (sum.e && !sum.g && !sum.a && !sum.f) return msg(sum.e, true);
    msg(sum.g + sum.a + sum.f ? `Filled ${sum.g} from your profile or saved answers, ${sum.a} drafts to review, ${sum.f} file(s). Check everything, then submit yourself.` : 'No matching empty fields found on this page.');
  } catch (e) { msg(e.message, true); }
};

$('start').onclick = async () => {
  const r = await bg('queue.start');
  if (!r.ok) return msg(r.error, true);
  msg(`Queue started with ${r.data} job${r.data === 1 ? '' : 's'}. A new tab opened with the first one.`);
};
$('stop').onclick = async () => { await bg('queue.stop'); msg('Queue stopped.'); };

refresh();
