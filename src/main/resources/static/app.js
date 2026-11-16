const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const csv = s => s.split(',').map(x => x.trim()).filter(Boolean);
const lines = s => s.split('\n').map(x => x.trim()).filter(Boolean);

async function api(path, opts = {}) {
  opts.headers = { ...(opts.headers || {}), 'X-JobPilot': '1' };
  if (opts.body && !(opts.body instanceof FormData)) {
    opts.headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(opts.body);
  }
  const r = await fetch('/api' + path, opts);
  const isJson = (r.headers.get('content-type') || '').includes('json');
  const data = isJson ? await r.json() : await r.text();
  if (!r.ok) throw new Error((data && data.error) || data || r.statusText);
  return data;
}

let toastTimer;
function toast(msg, isErr = false) {
  const t = $('#toast');
  t.textContent = msg;
  t.className = isErr ? 'err' : '';
  t.style.display = 'block';
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (t.style.display = 'none'), isErr ? 7000 : 3500);
}

async function busy(btn, label, fn) {
  const old = btn.textContent;
  btn.disabled = true;
  btn.textContent = label;
  try { return await fn(); }
  catch (e) { toast(e.message, true); }
  finally { btn.disabled = false; btn.textContent = old; }
}

/* ---------- tabs ---------- */
$$('.tab').forEach(b => b.onclick = () => {
  $$('.tab').forEach(x => x.classList.toggle('active', x === b));
  $$('main > section').forEach(sec => (sec.hidden = sec.id !== 'tab-' + b.dataset.tab));
  if (b.dataset.tab === 'settings') { loadSettings().catch(e => toast(e.message, true)); loadAnswers().catch(() => {}); }
});

/* ---------- profile ---------- */
function field(label, key, val, tag = 'input', rows = 3) {
  return tag === 'textarea'
    ? `<label>${label}<textarea data-k="${key}" rows="${rows}">${esc(val)}</textarea></label>`
    : `<label>${label}<input data-k="${key}" value="${esc(val)}"></label>`;
}

function addExp(e = {}) {
  const d = document.createElement('div');
  d.className = 'block';
  d.innerHTML = `<div class="grid2">${field('Title', 'title', e.title)}${field('Company', 'company', e.company)}
    ${field('Start', 'start', e.start)}${field('End', 'end', e.end)}</div>
    ${field('What you did (one bullet per line)', 'bullets', (e.bullets || []).join('\n'), 'textarea', 5)}
    <button type="button" class="link danger">Remove this role</button>`;
  $('button', d).onclick = () => d.remove();
  $('#exp').append(d);
}
function addProj(p = {}) {
  const d = document.createElement('div');
  d.className = 'block';
  d.innerHTML = `<div class="grid2">${field('Name', 'name', p.name)}${field('Tech used', 'tech', p.tech)}</div>
    ${field('Details (one bullet per line)', 'bullets', (p.bullets || []).join('\n'), 'textarea', 4)}
    <button type="button" class="link danger">Remove this project</button>`;
  $('button', d).onclick = () => d.remove();
  $('#proj').append(d);
}
function addEdu(e = {}) {
  const d = document.createElement('div');
  d.className = 'block';
  d.innerHTML = `<div class="grid2">${field('Degree', 'degree', e.degree)}${field('School', 'school', e.school)}
    ${field('Year', 'year', e.year)}</div>
    <button type="button" class="link danger">Remove</button>`;
  $('button', d).onclick = () => d.remove();
  $('#edu').append(d);
}
$('#addExp').onclick = () => addExp();
$('#addProj').onclick = () => addProj();
$('#addEdu').onclick = () => addEdu();

function fillProfile(p) {
  const set = (id, v) => ($('#' + id).value = v ?? '');
  set('fullName', p.fullName); set('headline', p.headline); set('email', p.email); set('phone', p.phone);
  set('location', p.location); set('years', p.yearsExperience || ''); set('github', p.github); set('linkedin', p.linkedin);
  set('summary', p.summary);
  set('targetTitles', (p.targetTitles || []).join(', '));
  set('prefLocs', (p.preferredLocations || []).join(', '));
  set('excluded', (p.excludedKeywords || []).join(', '));
  set('skills', (p.skills || []).join(', '));
  set('certs', (p.certifications || []).join('\n'));
  $('#remoteOk').checked = !!p.remoteOk;
  $('#exp').innerHTML = ''; $('#proj').innerHTML = ''; $('#edu').innerHTML = '';
  (p.experience || []).forEach(addExp);
  (p.projects || []).forEach(addProj);
  (p.education || []).forEach(addEdu);
}

const val = (b, k) => $(`[data-k="${k}"]`, b).value.trim();
function collectProfile() {
  return {
    fullName: $('#fullName').value.trim(), headline: $('#headline').value.trim(), email: $('#email').value.trim(),
    phone: $('#phone').value.trim(), location: $('#location').value.trim(),
    yearsExperience: parseFloat($('#years').value) || 0, github: $('#github').value.trim(),
    linkedin: $('#linkedin').value.trim(), summary: $('#summary').value.trim(), remoteOk: $('#remoteOk').checked,
    targetTitles: csv($('#targetTitles').value), preferredLocations: csv($('#prefLocs').value),
    excludedKeywords: csv($('#excluded').value), skills: csv($('#skills').value), certifications: lines($('#certs').value),
    experience: $$('#exp .block').map(b => ({ title: val(b, 'title'), company: val(b, 'company'), start: val(b, 'start'), end: val(b, 'end'), bullets: lines($('[data-k=bullets]', b).value) })),
    projects: $$('#proj .block').map(b => ({ name: val(b, 'name'), tech: val(b, 'tech'), bullets: lines($('[data-k=bullets]', b).value) })),
    education: $$('#edu .block').map(b => ({ degree: val(b, 'degree'), school: val(b, 'school'), year: val(b, 'year') }))
  };
}

$('#btnSave').onclick = e => busy(e.target, 'Saving…', async () => {
  fillProfile(await api('/profile', { method: 'PUT', body: collectProfile() }));
  await loadJobs();
  toast('Profile saved. Match scores updated.');
});

$('#btnImport').onclick = e => busy(e.target, 'Reading…', async () => {
  const f = $('#resumeFile').files[0];
  if (!f) throw new Error('Choose a resume file first');
  const fd = new FormData();
  fd.append('file', f);
  fillProfile(await api('/profile/import?mode=' + $('#importMode').value, { method: 'POST', body: fd }));
  await loadJobs();
  toast('Resume read. Review each section, then save.');
});

/* ---------- jobs ---------- */
let jobs = [];
const scoreClass = n => (n >= 70 ? 'hi' : n >= 40 ? 'mid' : 'lo');

function renderJobs() {
  const q = $('#filterText').value.toLowerCase();
  const min = parseInt($('#filterScore').value) || 0;
  const hide = $('#hideDone').checked;
  const rows = jobs.filter(j =>
    j.score >= min &&
    (!hide || (j.status !== 'SKIPPED' && j.status !== 'APPLIED')) &&
    (`${j.title} ${j.company}`).toLowerCase().includes(q));
  $('#count').textContent = `${rows.length} of ${jobs.length} jobs`;
  $('#emptyJobs').hidden = rows.length > 0;
  $('#jobsTable tbody').innerHTML = rows.map(j => `
    <tr>
      <td><span class="score ${scoreClass(j.score)}">${j.score}</span></td>
      <td><div class="title">${esc(j.title)}</div><div>${esc(j.company)}</div>
        <div class="skillline">${j.matchedSkills ? `<b>You have:</b> ${esc(j.matchedSkills)}` : ''}${j.missingSkills ? `<br>Asked for, not on your profile: ${esc(j.missingSkills)}` : ''}</div></td>
      <td>${esc(j.location)}${j.remote ? ' (remote)' : ''}<div class="muted">${esc(j.source)}</div></td>
      <td class="status">${esc(j.status.charAt(0) + j.status.slice(1).toLowerCase())}</td>
      <td><div class="actions">
        <button class="small primary" data-act="tailor" data-id="${j.id}">Tailor resume</button>
        ${j.url ? `<a href="${esc(j.url)}" target="_blank" rel="noopener noreferrer"><button class="small">Open</button></a>` : ''}
        <button class="small" data-act="APPLIED" data-id="${j.id}">Applied</button>
        <button class="small" data-act="SKIPPED" data-id="${j.id}">Skip</button>
        <button class="small danger" data-act="delete" data-id="${j.id}">Delete</button>
      </div></td>
    </tr>`).join('');
}

async function loadJobs() {
  jobs = await api('/jobs');
  renderJobs();
}
['filterText', 'filterScore', 'hideDone'].forEach(id => $('#' + id).addEventListener('input', renderJobs));

$('#jobsTable').addEventListener('click', async e => {
  const b = e.target.closest('button[data-act]');
  if (!b) return;
  const id = b.dataset.id, act = b.dataset.act;
  try {
    if (act === 'tailor') {
      b.disabled = true; b.textContent = 'Working…';
      const r = await api(`/jobs/${id}/tailor`, { method: 'POST' });
      const job = jobs.find(j => String(j.id) === id);
      $('#dlgTitle').textContent = `${job.title} at ${job.company}`;
      $('#dlgSkills').innerHTML = `<b>Matched:</b> ${esc(r.matchedSkills.join(', ') || 'none found')}<br>` +
        `<b>Gaps (not added to your resume):</b> ${esc(r.gapSkills.join(', ') || 'none')}`;
      $('#dlgLinks').innerHTML = `<a href="/api/jobs/${id}/resume.pdf"><button class="primary">Download PDF</button></a>
        <a href="/api/jobs/${id}/resume.docx"><button>Download Word</button></a>`;
      $('#dlgCover').value = r.coverLetter;
      $('#dlg').showModal();
      await loadJobs();
    } else if (act === 'delete') {
      if (!confirm('Delete this job?')) return;
      await api(`/jobs/${id}`, { method: 'DELETE' });
      await loadJobs();
    } else {
      await api(`/jobs/${id}/status?value=${act}`, { method: 'PATCH' });
      await loadJobs();
    }
  } catch (err) { toast(err.message, true); renderJobs(); }
});

$('#dlgClose').onclick = () => $('#dlg').close();
$('#dlgCopy').onclick = async () => {
  await navigator.clipboard.writeText($('#dlgCover').value);
  toast('Cover letter copied');
};

$('#btnFetch').onclick = e => busy(e.target, 'Searching…', async () => {
  const r = await api('/jobs/fetch', { method: 'POST', body: { query: $('#fetchQuery').value, location: $('#fetchLoc').value } });
  await loadJobs();
  toast(`Added ${r.added} new jobs, ${r.skipped} already saved.` + (r.errors.length ? ' Problems: ' + r.errors.join('; ') : ''), r.errors.length > 0 && r.added === 0);
});
$('#btnRescore').onclick = e => busy(e.target, 'Scoring…', async () => {
  await api('/jobs/rescore', { method: 'POST' });
  await loadJobs();
  toast('Scores updated');
});
$('#btnAddJob').onclick = e => busy(e.target, 'Adding…', async () => {
  await api('/jobs', { method: 'POST', body: { title: $('#mTitle').value, company: $('#mCompany').value, location: $('#mLocation').value, url: $('#mUrl').value, description: $('#mDesc').value } });
  ['mTitle', 'mCompany', 'mLocation', 'mUrl', 'mDesc'].forEach(id => ($('#' + id).value = ''));
  await loadJobs();
  toast('Job added');
});


/* ---------- settings: connect the browser ---------- */
let settingsCache = null;
const browserName = () => (navigator.brave ? 'Brave' : /Edg\//.test(navigator.userAgent) ? 'Edge' : 'Chrome');
const extPageUrl = () => ({ Brave: 'brave://extensions', Edge: 'edge://extensions', Chrome: 'chrome://extensions' }[browserName()]);
const extInstalled = () => document.documentElement.dataset.jobpilotExt === '1';
const ago = ms => {
  const s = Math.max(0, Math.round((Date.now() - ms) / 1000));
  return s < 60 ? `${s} seconds ago` : s < 3600 ? `${Math.round(s / 60)} minutes ago` : `${Math.round(s / 3600)} hours ago`;
};

function renderExtState() {
  const st = $('#extState'), btn = $('#btnPair'), last = $('#lastSeen');
  const seen = settingsCache && settingsCache.extensionLastSeen;
  const recent = seen && Date.now() - seen < 10 * 60 * 1000;
  if (!extInstalled()) {
    st.className = 'pill lo';
    st.textContent = 'Extension not detected yet. Finish step 2, then reload this page.';
    btn.disabled = true; btn.textContent = 'Connect extension';
  } else if (!seen) {
    st.className = 'pill mid';
    st.textContent = 'Extension found. Press Connect.';
    btn.disabled = false; btn.textContent = 'Connect extension';
  } else {
    st.className = 'pill hi';
    st.textContent = recent ? 'Connected ✓' : 'Connected earlier. Press Connect if scores stop appearing.';
    btn.disabled = false; btn.textContent = 'Reconnect';
  }
  last.textContent = seen ? `Last contact from the extension: ${ago(seen)}.` : '';
}

async function loadSettings() {
  const s = await api('/settings');
  settingsCache = s;
  $('#setServer').value = s.serverUrl;
  $('#setToken').value = s.token;
  $('#browserName').textContent = browserName();
  $('#extUrl').textContent = extPageUrl();
  $('#aiStatus').textContent = s.aiConfigured
    ? 'On. Free-text questions on application forms the extension cannot answer from your profile or saved answers get a short draft, shown in amber. Your profile and the job text are sent to Anthropic for each draft.'
    : 'Off. To turn it on, set the ANTHROPIC_API_KEY environment variable before starting JobPilot (Windows: set ANTHROPIC_API_KEY=your-key), then restart.';
  try {
    const p = await api('/profile');
    const t = (p.targetTitles && p.targetTitles[0]) || 'java developer';
    $('#openNaukri').href = `https://www.naukri.com/${t.toLowerCase().trim().replace(/[^a-z0-9]+/g, '-')}-jobs`;
    $('#openIndeed').href = `https://in.indeed.com/jobs?q=${encodeURIComponent(t)}`;
  } catch (e) { /* keep the default links */ }
  renderExtState();
}

async function loadAnswers() {
  const answers = await api('/answers');
  $('#emptyAns').hidden = answers.length > 0;
  $('#ansTable tbody').innerHTML = answers.map(a => `
    <tr><td>${esc(a.question)}</td><td>${esc(a.answer)}</td>
    <td><button class="small danger" data-del="${a.id}">Delete</button></td></tr>`).join('');
}

$('#copyExtUrl').onclick = async () => { await navigator.clipboard.writeText(extPageUrl()); toast('Address copied. Paste it into a new tab.'); };
$('#btnPair').onclick = () => {
  if (!settingsCache) return;
  window.postMessage({ source: 'jobpilot-ui', type: 'pair', token: settingsCache.token }, window.location.origin);
};
window.addEventListener('message', e => {
  if (e.source !== window || !e.data || e.data.source !== 'jobpilot-ext' || e.data.type !== 'paired') return;
  if (e.data.ok) toast('Extension connected. Open Naukri or Indeed to see scores.');
  else toast(e.data.error || 'Could not connect the extension', true);
  loadSettings().catch(() => {});
});
setInterval(() => { if (!$('#tab-settings').hidden) loadSettings().catch(() => {}); }, 4000);

$('#copyToken').onclick = async () => {
  await navigator.clipboard.writeText($('#setToken').value);
  toast('Token copied');
};
$('#regenToken').onclick = async () => {
  if (!confirm('Generate a new token? The extension will need to reconnect.')) return;
  try { await api('/settings/token/regenerate', { method: 'POST' }); await loadSettings(); toast('New token created. Press Connect extension again.'); }
  catch (e) { toast(e.message, true); }
};
$('#ansTable').addEventListener('click', async e => {
  const b = e.target.closest('[data-del]');
  if (!b) return;
  try { await api('/answers/' + b.dataset.del, { method: 'DELETE' }); await loadAnswers(); }
  catch (err) { toast(err.message, true); }
});

/* ---------- start ---------- */
(async () => {
  try {
    fillProfile(await api('/profile'));
    await loadJobs();
  } catch (e) { toast(e.message, true); }
})();
