package com.davidlang.vehicleexpensesautomated.ui.experiment

/**
 * Shared experiment HTML chrome: sticky filters, column hide, prev/next photo,
 * 500px default column cap. kind = pump | alignment | multiscale.
 */
object ExperimentReportHtml {

    enum class Kind { PUMP, ALIGNMENT, MULTISCALE }

    fun kindKey(kind: Kind): String = when (kind) {
        Kind.PUMP -> "pump"
        Kind.ALIGNMENT -> "alignment"
        Kind.MULTISCALE -> "multiscale"
    }

    fun css(): String {
        val hideCols = (1..40).joinToString("\n") { n ->
            """body.hide-col-$n #report [data-col="$n"] { display: none !important; }"""
        }
        return """
<style>
body { font-family: sans-serif; margin: 0; }
.ve-bar { position: sticky; top: 0; z-index: 30; background: #f7f7f7;
  border-bottom: 1px solid #bbb; padding: 8px 12px; font-size: 14px; }
.ve-bar .ctl { display: inline-block; margin: 3px 10px 3px 0; white-space: nowrap; }
.ve-bar .row { margin: 4px 0; }
#col-checks { display: flex; flex-wrap: wrap; align-items: center; }
#report { border-collapse: collapse; font-size: 18px; --col-max: 500px;
  table-layout: fixed; width: max-content; }
#report th, #report td { border: 1px solid #ccc; padding: 4px; text-align: center;
  vertical-align: top; word-wrap: break-word; }
#report th[data-col], #report td[data-col] {
  max-width: var(--col-max); width: var(--col-max); overflow: hidden; box-sizing: border-box;
}
#report.col-unlim { table-layout: auto; width: max-content; }
#report.col-unlim th[data-col], #report.col-unlim td[data-col] {
  max-width: none; width: auto; overflow: visible;
}
#report th { background: #eee; position: sticky; z-index: 5; }
#report img { max-width: 100% !important; height: auto; border: 1px solid #eee; margin-bottom: 2px; }
.res-table { width: 100%; border: none; font-size: 16px; }
.res-table th { background: #f0f0f0; }
body.hide-orig-details .orig-details { display: none; }
body.hide-dump-details .dump-details { display: none; }
body.hide-rec-crops .rec-crops { display: none; }
body.hide-look-ink-crops .look-ink-crops { display: none; }
.look-ink-crops img { max-width: none !important; height: auto; image-rendering: pixelated; }
$hideCols
.ocr-step { margin-bottom: 4px; border-bottom: 1px solid #eee; font-size: 18px; text-align: left; }
.stat { font-size: 10px; color: #666; }
</style>
""".trimIndent()
    }

    fun toolbar(kind: Kind, columnLabels: List<String>, metaHtml: String): String {
        val rec = if (kind == Kind.PUMP) {
            """<label class="ctl"><input type="checkbox" class="ve-rec-crops" checked> Rec crops</label>
    <label class="ctl"><input type="checkbox" class="ve-look-ink" checked> Look ink</label>"""
        } else ""
        val checks = StringBuilder()
        columnLabels.forEachIndexed { i, lab ->
            if (i == 0) return@forEachIndexed
            val esc = lab.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            checks.append(
                """<label class="ctl"><input type="checkbox" checked data-col="$i">$esc</label>""",
            )
        }
        return """
<div class="ve-bar">
  <div class="row">$metaHtml</div>
  <div class="row">
    <button type="button" class="ve-prev">Prev photo</button>
    <button type="button" class="ve-next">Next photo</button>
    <button type="button" class="ve-all">All columns</button>
    <button type="button" class="ve-none">No experiment columns</button>
    <label class="ctl"><input type="checkbox" class="ve-orig-details" checked>
      # / filename extra details (orig, hist, deskew, timing)</label>
    <label class="ctl"><input type="checkbox" class="ve-dump-details" checked> Dump details</label>
    $rec
    <label class="ctl">Column max width
      <input type="number" class="ve-col-max" value="500" min="80" step="10" style="width:4.5em;"> px</label>
    <label class="ctl"><input type="checkbox" class="ve-col-unlim"> unlimited</label>
  </div>
  <div class="row" id="col-checks">$checks</div>
</div>
""".trimIndent()
    }

    fun script(kind: Kind): String {
        val key = "ve-exp-html-ui:${kindKey(kind)}"
        val recJs = if (kind == Kind.PUMP) "true" else "false"
        return """
<script>
(function() {
  const KEY = "$key";
  const HAS_REC = $recJs;
  function reportEl() { return document.getElementById('report'); }
  function topBar() {
    return document.querySelector('.ve-bar');
  }
  function syncSticky() {
    const tb = topBar();
    if (!tb) return;
    const h = tb.getBoundingClientRect().height;
    document.querySelectorAll('#report thead th').forEach(function(th) { th.style.top = h + 'px'; });
  }
  function photoStarts() {
    const seen = {};
    const out = [];
    document.querySelectorAll('tr[data-photo]').forEach(function(tr) {
      const p = tr.getAttribute('data-photo');
      if (seen[p]) return;
      seen[p] = 1;
      out.push(tr);
    });
    return out;
  }
  function currentIndex() {
    const rows = photoStarts();
    var i = 0;
    var y = 80;
    for (var k = 0; k < rows.length; k++) {
      if (rows[k].getBoundingClientRect().top <= y + 4) i = k;
    }
    return i;
  }
  function goPhoto(d) {
    const rows = photoStarts();
    if (!rows.length) return;
    var i = currentIndex() + d;
    if (i < 0) i = 0;
    if (i >= rows.length) i = rows.length - 1;
    rows[i].scrollIntoView({ block: 'start' });
  }
  function applyWidth() {
    const report = reportEl();
    const unlim = document.querySelector('.ve-col-unlim');
    const colMax = document.querySelector('.ve-col-max');
    if (!unlim || !colMax || !report) return;
    if (unlim.checked) {
      report.classList.add('col-unlim');
      colMax.disabled = true;
    } else {
      var px = parseInt(colMax.value, 10);
      if (!px || px < 80) px = 500;
      colMax.value = px;
      report.style.setProperty('--col-max', px + 'px');
      report.classList.remove('col-unlim');
      colMax.disabled = false;
    }
    syncSticky();
  }
  function apply() {
    var src = topBar();
    if (!src) return;
    src.querySelectorAll('input[data-col]').forEach(function(cb) {
      var id = cb.getAttribute('data-col');
      var on = cb.checked;
      document.body.classList.toggle('hide-col-' + id, !on);
      document.querySelectorAll('.ve-bar input[data-col="' + id + '"]').forEach(function(o) { o.checked = on; });
    });
    var orig = document.querySelector('.ve-orig-details');
    var dump = document.querySelector('.ve-dump-details');
    document.body.classList.toggle('hide-orig-details', orig && !orig.checked);
    document.body.classList.toggle('hide-dump-details', dump && !dump.checked);
    if (HAS_REC) {
      var rec = document.querySelector('.ve-rec-crops');
      document.body.classList.toggle('hide-rec-crops', rec && !rec.checked);
      var lookInk = document.querySelector('.ve-look-ink');
      document.body.classList.toggle('hide-look-ink-crops', lookInk && !lookInk.checked);
    }
    applyWidth();
    save();
  }
  function save() {
    try {
      var st = { cols: {}, orig: true, dump: true, rec: true, lookInk: true, unlim: false, max: 500 };
      var src = topBar();
      if (src) {
        src.querySelectorAll('input[data-col]').forEach(function(cb) {
          st.cols[cb.getAttribute('data-col')] = cb.checked;
        });
      }
      var orig = document.querySelector('.ve-orig-details');
      var dump = document.querySelector('.ve-dump-details');
      var rec = document.querySelector('.ve-rec-crops');
      var lookInk = document.querySelector('.ve-look-ink');
      var unlim = document.querySelector('.ve-col-unlim');
      var colMax = document.querySelector('.ve-col-max');
      if (orig) st.orig = orig.checked;
      if (dump) st.dump = dump.checked;
      if (rec) st.rec = rec.checked;
      if (lookInk) st.lookInk = lookInk.checked;
      if (unlim) st.unlim = unlim.checked;
      if (colMax) st.max = parseInt(colMax.value, 10) || 500;
      localStorage.setItem(KEY, JSON.stringify(st));
    } catch (e) {}
  }
  function load() {
    try {
      var raw = localStorage.getItem(KEY);
      if (!raw) return;
      var st = JSON.parse(raw);
      if (st.cols) {
        document.querySelectorAll('.ve-bar input[data-col]').forEach(function(cb) {
          var id = cb.getAttribute('data-col');
          if (Object.prototype.hasOwnProperty.call(st.cols, id)) cb.checked = !!st.cols[id];
        });
      }
      document.querySelectorAll('.ve-orig-details').forEach(function(el) { if (st.orig !== undefined) el.checked = !!st.orig; });
      document.querySelectorAll('.ve-dump-details').forEach(function(el) { if (st.dump !== undefined) el.checked = !!st.dump; });
      document.querySelectorAll('.ve-rec-crops').forEach(function(el) { if (st.rec !== undefined) el.checked = !!st.rec; });
      document.querySelectorAll('.ve-look-ink').forEach(function(el) { if (st.lookInk !== undefined) el.checked = !!st.lookInk; });
      document.querySelectorAll('.ve-col-unlim').forEach(function(el) { if (st.unlim !== undefined) el.checked = !!st.unlim; });
      document.querySelectorAll('.ve-col-max').forEach(function(el) { if (st.max) el.value = st.max; });
    } catch (e) {}
  }
  function inBar(el) { return el && el.closest && el.closest('.ve-bar'); }
  document.addEventListener('change', function(e) {
    if (inBar(e.target)) apply();
  });
  document.addEventListener('input', function(e) {
    var t = e.target;
    if (t && t.classList && t.classList.contains('ve-col-max')) applyWidth();
  });
  document.addEventListener('click', function(e) {
    var t = e.target;
    if (!inBar(t)) return;
    if (t.closest('.ve-all')) {
      document.querySelectorAll('.ve-bar input[data-col]').forEach(function(cb) { cb.checked = true; });
      apply();
    } else if (t.closest('.ve-none')) {
      document.querySelectorAll('.ve-bar input[data-col]').forEach(function(cb) { cb.checked = false; });
      apply();
    } else if (t.closest('.ve-prev')) {
      goPhoto(-1);
    } else if (t.closest('.ve-next')) {
      goPhoto(1);
    }
  });
  function boot() { load(); apply(); }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
  setTimeout(boot, 0);
  window.addEventListener('resize', syncSticky);
})();
</script>
""".trimIndent()
    }

    fun documentHead(title: String, kind: Kind): String =
        "<!DOCTYPE html><html><head><meta charset=\"utf-8\"/><title>$title</title>\n${css()}\n${script(kind)}</head><body>\n"

    fun tableOpen(headerCells: List<String>): String {
        val sb = StringBuilder()
        sb.append("<table id=\"report\"><thead><tr>")
        headerCells.forEachIndexed { i, lab ->
            sb.append("<th data-col=\"$i\">$lab</th>")
        }
        sb.append("</tr></thead><tbody>\n")
        return sb.toString()
    }

    @Suppress("UNUSED_PARAMETER")
    fun footer(kind: Kind, columnLabels: List<String>, metaHtml: String): String =
        "</tbody></table>\n</body></html>\n"
}
