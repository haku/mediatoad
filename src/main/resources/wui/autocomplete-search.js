const searchAc = new autoComplete({
  name: "search",
  selector: "#search",
  wrapper: false,
  placeHolder: "search",
  data: {
    src: async (query) => {
      try {
        const source = await fetch(`../ac?mode=search&fragment=${escape(query)}`);
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
      item.innerHTML = `<span>${data.match}</span>`;
    },
  },
  query: (query) => {
    const x = searchAc.input.selectionStart;
    const e = query.lastIndexOf('t=', x);
    if (e < 0) return '';
    // TODO check for spaces, etc.
    // TODO what about quotes?
    const q = query.substring(e, x);
    console.log('query', '"' + query + '"', x, '"' + q + '"');
    return q;
  },
  trigger: (query) => {
    // This is mostly pointless right now but will make more sense
    // once query() is stricter about spaces etc.
    const t = query.startsWith('t=');
    console.log('trigger', t);
    return t;
  },
  //submit: true,
  events: {
    input: {
      selection: (event) => {
        const input = searchAc.input;
        const feedback = event.detail;
        const selection = feedback.selection.value.tag.trim();

        const x = input.selectionStart;
        const oldText = input.value;
        const e = oldText.lastIndexOf('t=', x);
        const newText = oldText.substring(0, e)
            + selection
            + oldText.substring(x);
        input.value = newText;
        const newX = x - (x - e) + selection.length;
        input.setSelectionRange(newX, newX);
      }
    }
  },
});
