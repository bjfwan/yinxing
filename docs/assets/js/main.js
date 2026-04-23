const turb = document.getElementById('lg-turb');
const specs = document.querySelector('.specs');
const specsBg = document.getElementById('specs-bg');
let t = 0;
let mouseX = 0.5, mouseY = 0.5;
let targetX = 0.5, targetY = 0.5;

document.addEventListener('mousemove', e => {
  targetX = e.clientX / window.innerWidth;
  targetY = e.clientY / window.innerHeight;
});

function lerp(a, b, n) { return a + (b - a) * n; }

function tick() {
  t += 0.004;
  mouseX = lerp(mouseX, targetX, 0.04);
  mouseY = lerp(mouseY, targetY, 0.04);
  const fx = (0.012 + Math.sin(t) * 0.004 + (mouseX - 0.5) * 0.006).toFixed(5);
  const fy = (0.018 + Math.cos(t * 0.8) * 0.004 + (mouseY - 0.5) * 0.006).toFixed(5);
  if (turb) turb.setAttribute('baseFrequency', `${fx} ${fy}`);
  const scale = 10 + Math.sin(t * 1.3) * 5 + Math.abs(mouseX - 0.5) * 8;
  if (turb && turb.nextElementSibling) turb.nextElementSibling.setAttribute('scale', scale.toFixed(2));
  if (specsBg) {
    specsBg.style.filter = 'url(#lg-filter)';
    const gx = 20 + mouseX * 60;
    const gy = 10 + mouseY * 40;
    specsBg.style.background = `radial-gradient(ellipse at ${gx}% ${gy}%, rgba(255,255,255,0.28) 0%, rgba(255,255,255,0.18) 100%)`;
  }
  requestAnimationFrame(tick);
}
tick();

document.querySelectorAll('.btn').forEach(btn => {
  btn.addEventListener('mousemove', e => {
    const r = btn.getBoundingClientRect();
    const cx = r.left + r.width / 2;
    const cy = r.top + r.height / 2;
    const dx = (e.clientX - cx) * 0.18;
    const dy = (e.clientY - cy) * 0.18;
    btn.style.transform = `translate(${dx}px, ${dy}px)`;
  });
  btn.addEventListener('mouseleave', () => {
    btn.style.transform = '';
    btn.style.transition = 'transform 0.5s cubic-bezier(0.23,1,0.32,1), background 0.3s, box-shadow 0.3s';
  });
  btn.addEventListener('mouseenter', () => {
    btn.style.transition = 'transform 0.1s ease, background 0.3s, box-shadow 0.3s';
  });

  btn.style.overflow = 'hidden';
  btn.addEventListener('click', e => {
    const r = btn.getBoundingClientRect();
    const ripple = document.createElement('span');
    const size = Math.max(r.width, r.height) * 2;
    ripple.style.cssText = `
      position:absolute;
      width:${size}px;height:${size}px;
      left:${e.clientX - r.left - size/2}px;
      top:${e.clientY - r.top - size/2}px;
      border-radius:50%;
      background:rgba(255,255,255,0.18);
      transform:scale(0);
      animation:ripple 0.6s ease-out forwards;
      pointer-events:none;
    `;
    btn.appendChild(ripple);
    setTimeout(() => ripple.remove(), 700);
  });
});

const iconImg = document.querySelector('.icon-block img');
if (iconImg) {
  iconImg.style.cursor = 'pointer';
  let shaking = false;
  iconImg.addEventListener('click', () => {
    if (shaking) return;
    shaking = true;
    iconImg.style.animation = 'iconShake 0.5s ease';
    setTimeout(() => {
      iconImg.style.animation = '';
      shaking = false;
    }, 500);
  });
}

const cursorOuter = document.createElement('div');
const cursorInner = document.createElement('div');
cursorOuter.style.cssText = `
  position:fixed;pointer-events:none;z-index:9999;
  width:500px;height:500px;border-radius:50%;
  background:radial-gradient(circle, rgba(184,136,42,0.18) 0%, rgba(184,136,42,0.06) 40%, transparent 70%);
  transform:translate(-50%,-50%);
  will-change:left,top;
  mix-blend-mode:multiply;
`;
cursorInner.style.cssText = `
  position:fixed;pointer-events:none;z-index:9999;
  width:120px;height:120px;border-radius:50%;
  background:radial-gradient(circle, rgba(184,136,42,0.35) 0%, transparent 70%);
  transform:translate(-50%,-50%);
  will-change:left,top;
`;
document.body.appendChild(cursorOuter);
document.body.appendChild(cursorInner);

let ocx = -999, ocy = -999, icx = -999, icy = -999, tcx = -999, tcy = -999;
document.addEventListener('mousemove', e => { tcx = e.clientX; tcy = e.clientY; });
document.addEventListener('mouseleave', () => { cursorOuter.style.opacity = '0'; cursorInner.style.opacity = '0'; });
document.addEventListener('mouseenter', () => { cursorOuter.style.opacity = '1'; cursorInner.style.opacity = '1'; });

(function cursorTick() {
  ocx = lerp(ocx, tcx, 0.06);
  ocy = lerp(ocy, tcy, 0.06);
  icx = lerp(icx, tcx, 0.18);
  icy = lerp(icy, tcy, 0.18);
  cursorOuter.style.left = ocx + 'px';
  cursorOuter.style.top  = ocy + 'px';
  cursorInner.style.left = icx + 'px';
  cursorInner.style.top  = icy + 'px';
  requestAnimationFrame(cursorTick);
})();

const observer = new IntersectionObserver(entries => {
  entries.forEach(entry => {
    if (!entry.isIntersecting) return;
    entry.target.querySelectorAll('.spec-value').forEach(el => {
      const text = el.textContent.trim();
      const num = parseFloat(text);
      if (isNaN(num)) return;
      const suffix = text.replace(/[\d.]/g, '');
      let start = 0;
      const dur = 900, step = 16;
      const inc = num / (dur / step);
      const timer = setInterval(() => {
        start = Math.min(start + inc, num);
        el.textContent = (Number.isInteger(num) ? Math.round(start) : start.toFixed(2)) + suffix;
        if (start >= num) clearInterval(timer);
      }, step);
    });
    observer.unobserve(entry.target);
  });
}, { threshold: 0.5 });
if (specs) observer.observe(specs);
