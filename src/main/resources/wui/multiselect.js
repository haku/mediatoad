ClickHelper = {};
(function() {
  const LONG_CLICK_MILLIS = 1000;

  ClickHelper.setupLongClick = (element, onClick, onLongClick, allowLongClick) => {
    let pressTimer;
    let longClicked = false;
    let x = -1;
    let y = -1;

    element.addEventListener('contextmenu', (event) => {
      if (!allowLongClick()) return;
      event.preventDefault();
    });

    const stopTimer = () => {
      clearTimeout(pressTimer);
      x = -1;
    };
    element.addEventListener('pointerup', stopTimer);
    element.addEventListener('pointercancel', stopTimer);

    element.addEventListener('pointermove', (event) => {
      if (x != -1) {
        if (Math.abs(event.screenX - x) > 5
          || Math.abs(event.screenY - y) > 5) {
          stopTimer();
          return false;
        }
      }
    });

    element.addEventListener('pointerdown', (event) => {
      longClicked = false;
      if (!allowLongClick()) return;

      event.preventDefault();
      x = event.screenX;
      y = event.screenY;

      pressTimer = window.setTimeout(() => {
        longClicked = true;
        onLongClick(event);
      }, LONG_CLICK_MILLIS);
      return false;
    });

    element.addEventListener('click', (event) => {
      if (longClicked) {
        event.preventDefault();
        return;
      }
      onClick(event);
    });
  };
})();

PopupHelper = {};
(function() {
  PopupHelper.hidePopup = (popup) => {
    popup.style.display = 'none';
    const obf = document.getElementById('popup-obfuscator');
    obf.classList.remove('is-visible');
  }

  PopupHelper.showPopup = (popup) => {
    const obf = document.getElementById('popup-obfuscator');
    obf.classList.add('is-visible');
    obf.addEventListener('click', (event) => {
      PopupHelper.hidePopup(popup);
      return false;
    }, {once: true});
    popup.style.display = 'block';
  }
})();

(function() {
  const selectedItems = new Set();
  const selInfo = document.getElementById('selection_info');
  const selMsg = document.getElementById('selection_msg');
  const selNone = document.getElementById('selection_none');
  const selAll = document.getElementById('selection_all');
  const selEditTags = document.getElementById('selection_edit_tags');

  const tagEditor = document.getElementById('tag_editor');
  const tagEditorTitle = document.querySelectorAll('#tag_editor .title')[0];
  const tagEditorNewTag = document.querySelectorAll('#tag_editor .newtag')[0];
  const tagEditorTags = document.querySelectorAll('#tag_editor .tags')[0];

  const selectedItemIds = () => {
    return Array.from(selectedItems).map((i) => i.getAttribute('item_id'));
  };

  const onSelectionChange = () => {
    if (selectedItems.size > 0) {
      selMsg.innerText = selectedItems.size + ' selected';
      selInfo.style.display = 'flex';
    }
    else {
      selInfo.style.display = 'none';
    }
  };

  const invertSelection = (item) => {
    if (selectedItems.has(item)) {
      selectedItems.delete(item);
      item.classList.remove('selected');
    }
    else {
      selectedItems.add(item);
      item.classList.add('selected');
    }
    onSelectionChange();
  };

  selNone.addEventListener('click', (event) => {
    selectedItems.clear();
    for (let i = 0; i < items.length; i++) {
      items[i].classList.remove('selected');
    }
    onSelectionChange();
  });
  selAll.addEventListener('click', (event) => {
  const items = document.getElementsByClassName('thumbnail');
    for (let i = 0; i < items.length; i++) {
      selectedItems.add(items[i]);
      items[i].classList.add('selected');
    }
    onSelectionChange();
  });

  const tagsPath = `${pathPrefix()}tags`;

  const setTagEditorTitle = (msg) => {
    if (msg) {
      if (Response.prototype.isPrototypeOf(msg)) {
        msg = 'failed: ' + msg.status + ' ' + msg.statusText;
      }
      tagEditorTitle.innerText = msg;
    }
    else {
      const item_ids = selectedItemIds();
      tagEditorTitle.innerText = 'Tags for ' + item_ids.length + ' items';
    }
  };

  const promptRemoveTag = (item_ids, tag, cls) => {
    if (!window.confirm('Tag: ' + tag + '\nClass: ' + cls + '\n\nRemove?')) {
      return;
    }
    console.log('remove', item_ids, tag, cls);

    setTagEditorTitle('Removing tag...');
    const req = new Request(tagsPath, {
      method: 'POST',
      cache: 'no-store',
      body: JSON.stringify({
        action: 'rmtag',
        tag: tag,
        cls: cls,
        ids: item_ids,
      }),
    });
    fetch(req).then(resp => {
      if (resp.status === 200) {
        tagEditorNewTag.value = tag;
        resp.json().then(j => showTagsInEditor(item_ids, j));
        setTagEditorTitle();
      }
      else {
        setTagEditorTitle(resp);
      }
    });
  };

  const showTagsInEditor = (item_ids, tf_list) => {
    tagEditorTags.innerHTML = '';

    const makeTagRow = (tf) => {
      const row = document.createElement('div');
      row.classList.add('tagrow');

      const label = document.createElement('label');
      label.classList.add('label');
      row.appendChild(label);

      const lblTag = document.createElement('p');
      lblTag.classList.add('tag');
      lblTag.innerText = tf['tag'];
      label.appendChild(lblTag);

      if (tf['cls']) {
        const lblCls = document.createElement('p');
        lblCls.classList.add('cls');
        lblCls.innerText = tf['cls'];
        label.appendChild(lblCls);
      }

      const lblCount = document.createElement('p');
      lblCount.classList.add('count');
      lblCount.innerText = '(' + tf['count'] + ')';
      row.appendChild(lblCount);

      const rm = document.createElement('button');
      rm.classList.add('rmbtn');
      rm.innerText = '❌';
      rm.addEventListener('click', (event) => {
        promptRemoveTag(item_ids, tf['tag'], tf['cls']);
      });
      row.appendChild(rm);

      tagEditorTags.appendChild(row);
    };

    tf_list.forEach((t) => makeTagRow(t));
  };

  const addNewTag = () => {
    const tag = tagEditorNewTag.value.trim();
    const item_ids = selectedItemIds();

    setTagEditorTitle('Adding tag...');
    const req = new Request(tagsPath, {
      method: 'POST',
      cache: 'no-store',
      body: JSON.stringify({
        action: 'addtag',
        tag: tag,
        ids: item_ids,
      }),
    });
    fetch(req).then(resp => {
      if (resp.status === 200) {
        resp.json().then(j => showTagsInEditor(item_ids, j));
        tagEditorNewTag.value = '';
        setTagEditorTitle();
      }
      else {
        setTagEditorTitle(resp);
      }
    });
  };
  tagEditorNewTag.addEventListener('keydown', (e) => {
    if (e.ctrlKey || e.shiftKey || e.altKey) return;
    if (event.keyCode === 13) {
      event.preventDefault();
      addNewTag();
    }
  });

  const showTagEditor = () => {
    const item_ids = selectedItemIds();
    setTagEditorTitle('loading...')
    tagEditorTags.innerHTML = '';
    PopupHelper.showPopup(tagEditor);

    const req = new Request(tagsPath, {
      method: 'POST',
      cache: 'no-store',
      body: JSON.stringify({
        action: 'gettags',
        ids: item_ids,
      }),
    });
    fetch(req).then(resp => {
      if (resp.status === 200) {
        resp.json().then(j => showTagsInEditor(item_ids, j));
        setTagEditorTitle();
      }
      else {
        setTagEditorTitle(resp);
      }
    });
  };
  selEditTags.addEventListener('click', (event) => {
    showTagEditor();
  });

  const items = document.getElementsByClassName('thumbnail');
  for (let i = 0; i < items.length; i++) {
    const t = items[i];
    const onClick = (event) => {
      if (selectedItems.size > 0) {
        event.preventDefault();
        invertSelection(t);
      }
    };
    const onLongClick = (event) => {
      invertSelection(t);
    };
    const allowLongClick = () => {
      return selectedItems.size < 1;
    };
    ClickHelper.setupLongClick(t, onClick, onLongClick, allowLongClick);
    t.addEventListener('keydown', (e) => {
      if (e.ctrlKey || e.shiftKey || e.altKey) return;
      if (event.key === 'v') {
        event.preventDefault();
        invertSelection(t);
      }
    });
  }
})();
