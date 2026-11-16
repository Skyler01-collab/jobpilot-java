// Greenhouse, Lever, Ashby, Workday, Workable, SmartRecruiters, iCIMS, Indeed smart apply.
(async () => {
  const jp = window.__jp;
  const o = (await jp.bg('opts')).data || {};
  jp.server = o.server || jp.server;
  jp.initPanel({ getJob: () => null });

  let started = false;
  async function tick() {
    const r = await jp.bg('queue.state');
    const st = r.ok ? r.data : null;
    if (st && st.active && !started) {
      started = true;
      if (window.top === window) jp.watchSubmitted();
      setTimeout(() => jp.highlightApply(!!o.autoClick), 1500);
      jp.autoFillLoop(() => ({ jobId: st.job.id }));
    }
  }
  tick();
  setInterval(tick, 2000);
})();
