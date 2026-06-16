/* confetti — lightweight, no deps */
(function(){
  const c = document.getElementById('confetti'); if(!c) return;
  const ctx = c.getContext('2d');
  let w,h,parts=[];
  const colors=["#ffcc4d","#ff6ec7","#00ff9c","#00e5ff","#ffffff"];
  function resize(){ w=c.width=innerWidth; h=c.height=innerHeight; }
  resize(); addEventListener('resize',resize);

  function spawn(n){
    for(let i=0;i<n;i++) parts.push({
      x:Math.random()*w, y:-20-Math.random()*h*0.3,
      r:4+Math.random()*6, c:colors[(Math.random()*colors.length)|0],
      vx:(Math.random()-0.5)*1.4, vy:1.5+Math.random()*3,
      rot:Math.random()*6.28, vr:(Math.random()-0.5)*0.3, sway:Math.random()*6.28
    });
  }
  spawn(160);

  (function draw(){
    ctx.clearRect(0,0,w,h);
    parts.forEach(p=>{
      p.sway+=0.02; p.x+=p.vx+Math.sin(p.sway)*0.6; p.y+=p.vy; p.rot+=p.vr;
      if(p.y>h+20){ p.y=-20; p.x=Math.random()*w; }
      ctx.save(); ctx.translate(p.x,p.y); ctx.rotate(p.rot);
      ctx.fillStyle=p.c; ctx.fillRect(-p.r/2,-p.r/2,p.r,p.r*0.6); ctx.restore();
    });
    requestAnimationFrame(draw);
  })();

  // burst on cake click
  document.getElementById('cakeImg')?.addEventListener('click',()=>spawn(80));
})();
