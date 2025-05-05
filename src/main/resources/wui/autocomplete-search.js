// Docs: https://tarekraafat.github.io/autoComplete.js/#/configuration
function lastSearchTermStart(query, x) {
  let e = -1;
  // TODO support quotes.
  const space = query.lastIndexOf(' ', x) + 1;
  if (space === 0 || space < x) e = space;
  return e;
}
function isValidSearchTerm(term) {
  // TODO support quotes.
  return /^-?[tT][=~][^ ]+$/.test(term);
}
function removeMatchOpertor(term) {
  return term.replace(/^-?[tT][=~]/, '');
}
function pathPrefix() {
  // TODO something less ugly for knowing when to need a relative path.
  const p = window.location.pathname;
  if (p.includes('/d/') || p.includes('/i/') || p.includes('/w/')) {
    return '../';
  }
  return '';
}
const searchAc = new autoComplete({
  name: "search",
  selector: "#search",
  wrapper: false,
  data: {
    src: async (query) => {
      try {
        const source = await fetch(`${pathPrefix()}ac?mode=search&fragment=${encodeURIComponent(query)}`);
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
  query: (query) => {
    const x = searchAc.input.selectionStart;
    const e = lastSearchTermStart(query, x);
    if (e < 0) return '';
    // TODO check for spaces, etc.
    // TODO what about quotes?
    let ret = query.substring(e, x);
    if (!/^-?[tT][=~]/.test(ret)) {
      ret = 't~' + ret;
    }
    return ret;  // This is then validated by trigger().
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
      keydown: (event) => {
        switch (event.keyCode) {
          case 13:  // enter
            if (searchAc.cursor >= 0) {
              event.preventDefault();
              searchAc.select(searchAc.cursor);
            }
            break;

          case 27:  // escape
            searchAc.close();
            break;

          case 38:  // up
          case 40:  // down
            event.preventDefault();
            searchAc.goTo(searchAc.cursor + (event.keyCode === 40 ? 1 : -1));
            break;
        }
      },
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
      close: (event) => {
        searchAc.cursor = -1;
      },
    }
  },
});
function clickLinkById(id) {
  let a = document.getElementById(id);
  if (a) a.click();
}
document.addEventListener('keydown', (e) => {
  if (e.ctrlKey || e.shiftKey) return;
  if (!e.altKey) {
    switch (e.target.tagName.toLowerCase()) {
      case 'input':
      case 'textarea':
        return;
    }
  }
  switch (e.key) {
    case '/':
      event.preventDefault();
      searchAc.input.focus();
      break;
    case 'u':
      event.preventDefault();
      clickLinkById('up');
      break;
  }
});
