MenuHelper = {};
(function() {
  MenuHelper.setup = (holder_id) => {
    const btn = document.querySelector('#' + holder_id + '>button');
    if (!btn) return;
    const drop = document.querySelector('#' + holder_id + '>div');
    btn.addEventListener('click', (event) => {
      event.preventDefault();
      if (drop.style.visibility === 'visible') {
        drop.style.visibility = 'collapse';
        btn.classList.remove('showing');
      }
      else {
        drop.style.visibility = 'visible';
        btn.classList.add('showing');
      }
    });
  }
})();

(function() {
  MenuHelper.setup('login_menu');
  MenuHelper.setup('search_menu');
})();
