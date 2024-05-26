ClickHelper = {};
(function() {
  const LONG_CLICK_MILLIS = 1000;

  ClickHelper.setupLongClick = function(element, onClick, onLongClick) {
    let pressTimer;
    let longClicked = false;
    let x = -1;
    let y = -1;

    element.addEventListener('contextmenu', function(event) {
      event.preventDefault();
    });

    element.addEventListener('pointerup', function(event) {
      x = -1;
      clearTimeout(pressTimer);
      return false;
    });

    element.addEventListener('pointermove', function(event) {
      if (x != -1) {
        if (Math.abs(event.screenX - x) > 5
          || Math.abs(event.screenY - y) > 5) {
          clearTimeout(pressTimer);
          x = -1;
          return false;
        }
      }
    });

    element.addEventListener('pointerdown', function(event) {
      event.preventDefault();
      x = event.screenX;
      y = event.screenY;

      longClicked = false;
      pressTimer = window.setTimeout(function() {
        longClicked = true;
        onLongClick(event);
      }, LONG_CLICK_MILLIS);
      return false;
    });

    element.addEventListener('click', function(event) {
      if (longClicked) {
        event.preventDefault();
        return;
      }
      onClick(event);
    });
  };
})();

(function() {
  const selectedItems = new Set();
  const selInfo = document.getElementById('selection_info');
  const selMsg = document.getElementById('selection_msg');
  const selEditTags = document.getElementById('selection_edit_tags');

  var onSelectionChange = function() {
    if (selectedItems.size > 0) {
      selMsg.innerText = selectedItems.size + ' selected';
      selInfo.style.display = 'block';
    }
    else {
      selInfo.style.display = 'none';
    }
  };

  const invertSelection = function(item) {
    if (selectedItems.has(item)) {
      selectedItems.delete(item);
      item.classList.remove('selected');
    }
    else {
      selectedItems.add(item);
      item.classList.add('selected');
    }
    onSelectionChange();
  }

  selEditTags.addEventListener('click', function(event) {
    const item_ids = Array.from(selectedItems).map((i) => i.getAttribute('item_id'));
    // TODO show tag edit UI.
    console.log('items:', item_ids)
  });

  const items = document.getElementsByClassName('thumbnail');
  for (let i = 0; i < items.length; i++) {
    const t = items[i];
    const onClick = function(event) {
      if (selectedItems.size > 0) {
        event.preventDefault();
        invertSelection(t);
      }
    };
    const onLongClick = function(event) {
      invertSelection(t);
    };
    ClickHelper.setupLongClick(t, onClick, onLongClick);
  }
})();