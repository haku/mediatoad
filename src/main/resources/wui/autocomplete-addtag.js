const addTagAc = new autoComplete({
  name: "addTag",
  selector: "#addTag",
  wrapper: false,
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
  events: {
    input: {
      keydown: (event) => {
        switch (event.keyCode) {
          case 13:  // enter
            if (addTagAc.cursor >= 0) {
              event.preventDefault();
              addTagAc.select(addTagAc.cursor);
            }
            break;

          case 27:  // escape
            addTagAc.close();
            break;

          case 38:  // up
          case 40:  // down
            event.preventDefault();
            addTagAc.goTo(addTagAc.cursor + (event.keyCode === 40 ? 1 : -1));
            break;
        }
      },
      selection: (event) => {
        const input = addTagAc.input;
        const feedback = event.detail;
        const selection = feedback.selection;
        input.value = selection.value.tag;
      },
      close: (event) => {
        addTagAc.cursor = -1;
      },
    }
  },
});
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
    case 't':
      event.preventDefault();
      addTagAc.input.focus();
      break;
    case 'f':
    case 'n':
      event.preventDefault();
      clickLinkById('next');
      break;
    case 'b':
    case 'p':
      event.preventDefault();
      clickLinkById('previous');
      break;
  }
});
