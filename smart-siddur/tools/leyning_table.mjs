// Prints a JSON table of the weekday (non-Shabbat, non-Yom-Tov) Torah readings for a range of
// dates, for Israel and the diaspora, using Hebcal's leyning rules. Run via tools/build_torah.py.
import {HDate, HebrewCalendar, flags} from '@hebcal/core';
import {getLeyningOnDate} from '@hebcal/leyning';

const [from, to] = process.argv.slice(2);
const out = {il: {}, chul: {}};

function aliyot(obj) {
  return Object.keys(obj).filter(k => /^\d+$/.test(k)).sort((a, b) => a - b).map(k => obj[k]);
}

for (let d = new Date(from + 'T12:00:00Z'); d <= new Date(to + 'T12:00:00Z'); d.setUTCDate(d.getUTCDate() + 1)) {
  if (d.getUTCDay() === 6) continue; // Shabbat
  const hd = new HDate(new Date(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate()));
  const iso = d.toISOString().slice(0, 10);
  for (const il of [true, false]) {
    const chag = HebrewCalendar.getHolidaysOnDate(hd, il)?.some(e => e.getFlags() & flags.CHAG);
    if (chag) continue; // Yom Tov / Yom Kippur readings aren't part of the weekday siddur
    const readings = getLeyningOnDate(hd, il, true) ?? [];
    const day = {};
    for (const r of readings) {
      const en = r.name?.en ?? '';
      if (en.startsWith('Shushan Purim')) continue; // only in Jerusalem
      const list = r.type === 'weekday' ? r.weekday : r.fullkriyah;
      if (!list) continue;
      const mincha = en.includes('(Mincha)');
      const entry = {
        name: r.name?.he ?? '',
        parsha: r.type === 'weekday' ? r.parshaNum : undefined,
        // At Mincha of a fast day the third aliyah is the maftir ("M")
        aliyot: [...aliyot(list), ...(mincha && list.M ? [list.M] : [])].map(a => ({k: a.k, b: a.b, e: a.e})),
        haft: r.haft ? [].concat(r.haft).map(h => ({k: h.k, b: h.b, e: h.e})) : undefined,
      };
      day[mincha ? 'mincha' : 'shacharit'] = entry;
    }
    if (Object.keys(day).length) out[il ? 'il' : 'chul'][iso] = day;
  }
}
process.stdout.write(JSON.stringify(out));
