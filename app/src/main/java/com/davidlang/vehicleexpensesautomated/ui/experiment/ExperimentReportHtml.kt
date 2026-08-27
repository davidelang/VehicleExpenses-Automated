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

    fun css(): String = """
<style>
body { font-family: sans-serif; margin: 0; }
.ve-bar { position: sticky; top: 0; z-index: 30; background: #f7f7f7;
  border-bottom: 1px solid #bbb; padding: 8px 12px; font-size: 14px; }
.ve-bar.bottom { position: sticky; bottom: 0; top: auto; border-top: 1px solid #bbb; border-bottom: none; }
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
</style>
""".trimIndent()

    fun toolbar(kind: Kind, columnLabels: List<String>, metaHtml: String, bottom: Boolean): String {
        val rec = if (kind == Kind.PUMP) {
            """<label class="ctl"><input type="checkbox" class="ve-rec-crops" checked> Rec crops</label>"""
        } else ""
        val checks = StringBuilder()
        columnLabels.forEachIndexed { i, lab ->
            if (i == 0) return@forEachIndexed
            val esc = lab.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            checks.append(
                """<label class="ctl"><input type="checkbox" checked data-col="$i">$esc</label>""",
            )
        }
        val cls = if (bottom) "ve-bar bottom" else "ve-bar"
        return """
<div class="$cls">
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
  <div class="row" id="${if (bottom) "col-checks-bottom" else "col-checks"}">$checks</div>
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
  const report = document.getElementById('report');
  const bars = document.querySelectorAll('.ve-bar');
  function syncSticky() {
    const topBar = document.querySelector('.ve-bar:not(.bottom)');
    if (!topBar) return;
    const h = topBar.getBoundingClientRect().height;
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
    document.querySelectorAll('.ve-bar input[data-col]').forEach(function(cb) {
      var id = cb.getAttribute('data-col');
      var on = cb.checked;
      document.querySelectorAll('#report [data-col="' + id + '"]').forEach(function(el) {
        el.style.display = on ? '' : 'none';
      });
    });
    var orig = document.querySelector('.ve-orig-details');
    var dump = document.querySelector('.ve-dump-details');
    document.body.classList.toggle('hide-orig-details', orig && !orig.checked);
    document.body.classList.toggle('hide-dump-details', dump && !dump.checked);
    if (HAS_REC) {
      var rec = document.querySelector('.ve-rec-crops');
      document.body.classList.toggle('hide-rec-crops', rec && !rec.checked);
    }
    applyWidth();
    save();
  }
  function save() {
    try {
      var st = { cols: {}, orig: true, dump: true, rec: true, unlim: false, max: 500 };
      document.querySelectorAll('.ve-bar:not(.bottom) input[data-col]').forEach(function(cb) {
        st.cols[cb.getAttribute('data-col')] = cb.checked;
      });
      var orig = document.querySelector('.ve-orig-details');
      var dump = document.querySelector('.ve-dump-details');
      var rec = document.querySelector('.ve-rec-crops');
      var unlim = document.querySelector('.ve-col-unlim');
      var colMax = document.querySelector('.ve-col-max');
      if (orig) st.orig = orig.checked;
      if (dump) st.dump = dump.checked;
      if (rec) st.rec = rec.checked;
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
      document.querySelectorAll('.ve-col-unlim').forEach(function(el) { if (st.unlim !== undefined) el.checked = !!st.unlim; });
      document.querySelectorAll('.ve-col-max').forEach(function(el) { if (st.max) el.value = st.max; });
    } catch (e) {}
  }
  function bindBar(bar) {
    bar.addEventListener('change', apply);
    bar.querySelectorAll('.ve-col-max').forEach(function(el) {
      el.addEventListener('input', applyWidth);
      el.addEventListener('change', applyWidth);
    });
    bar.querySelectorAll('.ve-all').forEach(function(b) {
      b.onclick = function() {
        document.querySelectorAll('.ve-bar input[data-col]').forEach(function(cb) { cb.checked = true; });
        apply();
      };
    });
    bar.querySelectorAll('.ve-none').forEach(function(b) {
      b.onclick = function() {
        document.querySelectorAll('.ve-bar input[data-col]').forEach(function(cb) { cb.checked = false; });
        apply();
      };
    });
    bar.querySelectorAll('.ve-prev').forEach(function(b) { b.onclick = function() { goPhoto(-1); }; });
    bar.querySelectorAll('.ve-next').forEach(function(b) { b.onclick = function() { goPhoto(1); }; });
  }
  bars.forEach(bindBar);
  load();
  apply();
  window.addEventListener('resize', syncSticky);
})();
</script>
""".trimIndent()
    }

    fun documentHead(title: String): String =
        "<!DOCTYPE html><html><head><meta charset=\"utf-8\"/><title>$title</title>\n${css()}</head><body>\n"

    fun tableOpen(headerCells: List<String>): String {
        val sb = StringBuilder()
        sb.append("<table id=\"report\"><thead><tr>")
        headerCells.forEachIndexed { i, lab ->
            sb.append("<th data-col=\"$i\">$lab</th>")
        }
        sb.append("</tr></thead><tbody>\n")
        return sb.toString()
    }

    fun footer(kind: Kind, columnLabels: List<String>, metaHtml: String): String =
        "</tbody></table>\n" +
            toolbar(kind, columnLabels, metaHtml, bottom = true) + "\n" +
            script(kind) + "\n</body></html>\n"
}
