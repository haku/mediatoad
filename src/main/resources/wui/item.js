(function() {
  const tagsPath = `${pathPrefix()}tags`;
  const form = document.getElementById('addtag_form');
  const newtag = document.getElementById('addTag');
  const submit = document.querySelectorAll('#addtag_form input[type="submit"]')[0];
  const tagrow  = document.querySelectorAll('.tag_row')[0];

  const updateTagRow = (tf_list) => {
    tagrow.innerHTML = '';

    const makeTagItem = (tf) => {
      const atag = document.createElement('a');
      atag.classList.add('item');
      atag.innerText = tf['tag'];
      atag.setAttribute('href', '../search?query=' + encodeURIComponent(tf['search']));
      tagrow.appendChild(atag);
      tagrow.appendChild(document.createTextNode(" "));  // match spacing from template new lines.
    };

    tf_list.forEach((t) => makeTagItem(t));
  };

  form.addEventListener('submit', (event) => {
    event.preventDefault();

    const tag = newtag.value.trim();
    const item_id = form.getAttribute('item_id');

    submit.disabled = true;
    const req = new Request(tagsPath, {
      method: 'POST',
      cache: 'no-store',
      body: JSON.stringify({
        action: 'addtag',
        tag: tag,
        ids: [item_id],
      }),
    });
    fetch(req).then(resp => {
      if (resp.status === 200) {
        resp.json().then(j => updateTagRow(j));
        newtag.value = '';
        submit.disabled = false;
      }
      else {
        console.log(resp);
        // TODO show error.
      }
    });
    return false;
  });
})();

