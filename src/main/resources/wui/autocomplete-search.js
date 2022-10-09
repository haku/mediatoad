// Docs: https://tarekraafat.github.io/autoComplete.js/#/configuration
function lastSearchTermStart(query, x) {
  return ['t=', 't~', 'T=', 'T~'].map(p => query.lastIndexOf(p, x)).reduce((l, x) => x > l ? x : l);
}
function isValidSearchTerm(term) {
  // This will make make more sense once quotes etc work?
  return /^-?[tT][=~][^ ]+$/.test(term);
}
function removeMatchOpertor(term) {
  return term.replace(/^-?[tT][=~]/, '');
}
function pathPrefix() {
  // TODO something less ugly for knowing when to need a relative path.
  const p = window.location.pathname;
  if (p.includes('/i/') || p.includes('/w/')) {
    return '../';
  }
  return '';
}
const searchAc = new autoComplete({
  name: "search",
  selector: "#search",
  wrapper: false,
  placeHolder: "search",
  data: {
    src: async (query) => {
      try {
        const source = await fetch(`${pathPrefix()}ac?mode=search&fragment=${escape(query)}`);
        const data = await source.json();
        return data;
      } catch (error) {
        return error;
      }
    },
    keys: ['tag'],
  },
  resultsList: {
    maxResults: 50,
    //tabSelect: true,
  },
  resultItem: {
    highlight: true,
    element: (item, data) => {
      item.innerHTML = `<span>${data.match}</span><span>(${data.value.count})</span>`;
    },
  },
  submit: true,
  query: (query) => {
    const x = searchAc.input.selectionStart;
    const e = lastSearchTermStart(query, x);
    if (e < 0) return '';
    // TODO check for spaces, etc.
    // TODO what about quotes?
    return query.substring(e, x);
    // This is then validated by trigger().
  },
  trigger: isValidSearchTerm,
  searchEngine: (query, record) => {
    const q = removeMatchOpertor(query);
    const x = record.indexOf(q);
    if (x >= 0) {
        record = record.replace(q, `<mark>${q}</mark>`);
    }
    return record;
  },
  events: {
    input: {
      selection: (event) => {
        const input = searchAc.input;
        const feedback = event.detail;
        const selection = feedback.selection.value.tag.trim();

        const x = input.selectionStart;
        const oldText = input.value;
        const e = lastSearchTermStart(oldText, x);
        const newText = oldText.substring(0, e)
            + selection
            + oldText.substring(x);
        input.value = newText;
        const newX = x - (x - e) + selection.length;
        input.setSelectionRange(newX, newX);
      },
      open: (event) => {
        searchAc.submit = false;
      },
      close: (event) => {
        searchAc.submit = true;
      },
    }
  },
});
