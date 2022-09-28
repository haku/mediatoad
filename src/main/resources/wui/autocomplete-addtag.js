const addTagAc = new autoComplete({
  name: "addTag",
  selector: "#addTag",
  placeHolder: "add tag",
  data: {
    src: async (query) => {
      try {
        const source = await fetch(`../ac?mode=addtag&fragment=${escape(query)}`);
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
  events: {
    input: {
      selection: (event) => {
        const input = addTagAc.input;
        const feedback = event.detail;
        const selection = feedback.selection;
        input.value = selection.value.tag;
      },
      open: (event) => {
        addTagAc.submit = false;
      },
      close: (event) => {
        addTagAc.submit = true;
      },
    }
  },
});
