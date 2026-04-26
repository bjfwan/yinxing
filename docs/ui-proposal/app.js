/* =====================================================
   银杏 Launcher · UI 优化提案 · 交互逻辑
   ===================================================== */

(function () {
  'use strict';

  // ---------- THEME TOGGLE ----------
  const root = document.documentElement;
  const toggle = document.getElementById('themeToggle');
  const STORAGE_KEY = 'yinxing-doc-theme';

  function applyTheme(name) {
    root.setAttribute('data-theme', name);
    try { localStorage.setItem(STORAGE_KEY, name); } catch (_) {}
  }

  // initial
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === 'dark' || saved === 'light') {
      applyTheme(saved);
    } else if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
      applyTheme('dark');
    }
  } catch (_) {}

  if (toggle) {
    toggle.addEventListener('click', function () {
      const next = root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
      applyTheme(next);
    });
  }

  // ---------- CALL BUTTON HOVER FEEDBACK ----------
  // Subtle press-down animation on the new call buttons inside phone mockups
  const callBtns = document.querySelectorAll('.phone .call-btn-after');
  callBtns.forEach(function (btn) {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      btn.style.transition = 'transform .15s ease';
      btn.style.transform = 'scale(.94)';
      setTimeout(function () { btn.style.transform = ''; }, 180);
    });
  });

  // ---------- SMOOTH SCROLL HIGHLIGHT ----------
  const navLinks = document.querySelectorAll('.topnav a');
  const sections = Array.from(document.querySelectorAll('section[id]'));

  function onScroll() {
    const y = window.scrollY + 120;
    let active = sections[0];
    for (let i = 0; i < sections.length; i++) {
      if (sections[i].offsetTop <= y) active = sections[i];
    }
    navLinks.forEach(function (a) {
      a.style.color = '';
      a.style.background = '';
      if (a.getAttribute('href') === '#' + active.id) {
        a.style.color = 'var(--ginkgo-deep)';
        a.style.background = 'var(--ginkgo-soft)';
      }
    });
  }
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();

})();
