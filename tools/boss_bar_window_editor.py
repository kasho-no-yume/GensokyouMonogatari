"""Build an offline rectangle editor for boss_bar_art.py --windows-file.

python tools/boss_bar_window_editor.py
Open build/bossbar/window_editor.html, draw each inner opening and export JSON.
Coordinates are pixels of the original image, with exclusive right/bottom edges.
"""
import argparse
import base64
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ai', type=Path, default=ROOT / 'build/bossbar/raw_v4_0.png')
    parser.add_argument('--windows-file', type=Path,
                        default=ROOT / 'tools/textures/boss_bar_windows_v4.json')
    parser.add_argument('--out', type=Path, default=ROOT / 'build/bossbar/window_editor.html')
    args = parser.parse_args()
    data = json.loads(args.windows_file.read_text(encoding='utf-8'))
    image = base64.b64encode(args.ai.read_bytes()).decode('ascii')
    html = TEMPLATE.replace('__RECTS__', json.dumps(data)).replace('__IMAGE__', image)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(html, encoding='utf-8')
    print(args.out)


TEMPLATE = r'''<!doctype html>
<html lang="zh-CN"><meta charset="utf-8"><title>BOSS 血槽框选</title>
<style>
body{margin:20px;background:#242424;color:#eee;font:16px system-ui}
.controls{position:sticky;top:0;background:#242424;padding:12px;z-index:1}
button,select,input{font:inherit;margin:5px;padding:5px}input[type=number]{width:85px}
canvas{max-width:100%;height:auto;cursor:crosshair;background:white;touch-action:none}
pre{white-space:pre-wrap} .hint{color:#ccc}label{white-space:nowrap}
</style>
<div class="controls">
<b>选择阶级，再在原图上拖出该阶的血槽内口。</b>
<p class="hint">沿着真正可见框线的内侧框选，别把外框和装饰包括进去。红框是选区，蓝框是当前阶；其他阶用黄色。
这里显示的是原始画作，导出的坐标会用于重新收口，不是修改血量比例。</p>
<select id="tier"><option value="1">1 绿</option><option value="2">2 蓝</option>
<option value="3">3 金</option><option value="4">4 红</option><option value="5">5 紫</option></select>
<label>左<input id="x0" type="number"></label><label>上<input id="y0" type="number"></label>
<label>右<input id="x1" type="number"></label><label>下<input id="y1" type="number"></label>
<button id="undo">撤销本阶修改</button><button id="save">导出 windows_manual.json</button>
<label>导入 JSON <input id="load" type="file" accept=".json"></label>
<label>显示宽度<select id="zoom"><option>728</option><option>1092</option><option>1456</option></select></label>
<div id="status" aria-live="polite"></div></div>
<canvas id="canvas"></canvas>
<script>
const initial=__RECTS__, rects=structuredClone(initial), image=new Image();
const canvas=document.querySelector('#canvas'),ctx=canvas.getContext('2d');
const tier=document.querySelector('#tier'),status=document.querySelector('#status');
const fields=['x0','y0','x1','y1'].map(id=>document.getElementById(id));
let start=null,draft=null;
function sync(){fields.forEach((e,i)=>e.value=rects[tier.value].window[i]);draw()}
function draw(){
 ctx.clearRect(0,0,canvas.width,canvas.height);ctx.drawImage(image,0,0);
 for(const [key,value] of Object.entries(rects)){
  const [x,y,r,b]=value.window;ctx.strokeStyle=key===tier.value?'#007cff':'#e0a000';
  ctx.lineWidth=key===tier.value?4:2;ctx.strokeRect(x,y,r-x,b-y);
  ctx.font='bold 24px sans-serif';ctx.fillStyle=ctx.strokeStyle;ctx.fillText(key,x+5,y-8);
 }
 if(draft){ctx.strokeStyle='#ff1744';ctx.lineWidth=4;ctx.strokeRect(draft[0],draft[1],draft[2]-draft[0],draft[3]-draft[1])}
}
function point(e){const r=canvas.getBoundingClientRect();return [
 Math.max(0,Math.min(image.width,Math.round((e.clientX-r.left)*canvas.width/r.width))),
 Math.max(0,Math.min(image.height,Math.round((e.clientY-r.top)*canvas.height/r.height)))];}
function commit(box){
 if(!box.every(Number.isFinite)||box[0]<1||box[1]<1||box[2]>=image.width||box[3]>=image.height||box[2]<=box[0]||box[3]<=box[1]){status.textContent='选区必须有正宽高，且四边位于原图内部';return false}
 const old=rects[tier.value],height=box[3]-box[1];
 // Keep the old outer vertical margin, but expand it if the new opening
 // crosses the old frame. The compose pipeline requires strict nesting.
 const rim=Math.max(4,Math.round(height*.14));
 old.window=box;old.frame=[Math.min(old.frame[0],box[0]-1),
  Math.max(0,Math.min(old.frame[1],box[1]-rim)),
  Math.max(old.frame[2],box[2]+1),Math.min(image.height,Math.max(old.frame[3],box[3]+rim))];
 status.textContent=`第 ${tier.value} 阶：${box.join(', ')}，宽 ${box[2]-box[0]} × 高 ${height} 原图像素`;
 sync();return true;
}
canvas.onpointerdown=e=>{start=point(e);canvas.setPointerCapture(e.pointerId)};
canvas.onpointermove=e=>{if(!start)return;const p=point(e);draft=[Math.min(start[0],p[0]),Math.min(start[1],p[1]),Math.max(start[0],p[0]),Math.max(start[1],p[1])];draw()};
canvas.onpointerup=e=>{if(draft)commit(draft);start=null;draft=null;draw()};
canvas.onpointercancel=()=>{start=null;draft=null;draw()};
fields.forEach(e=>e.onchange=()=>commit(fields.map(f=>Number(f.value))));
tier.onchange=sync;
document.querySelector('#undo').onclick=()=>{rects[tier.value]=structuredClone(initial[tier.value]);sync()};
document.querySelector('#save').onclick=()=>{
 const blob=new Blob([JSON.stringify(rects,null,2)],{type:'application/json'});
 const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download='windows_manual.json';a.click();URL.revokeObjectURL(url);
 status.textContent='已导出。把 JSON 放到 build/bossbar/windows_manual.json 后运行收口命令。';
};
document.querySelector('#load').onchange=async e=>{
 try{const data=JSON.parse(await e.target.files[0].text());
 for(let t=1;t<=5;t++)if(!Array.isArray(data[t]?.window)||data[t].window.length!==4||!data[t].window.every(Number.isFinite))throw Error('需要五阶的 window 坐标');
 for(let t=1;t<=5;t++)rects[t]={window:data[t].window,frame:data[t].frame||structuredClone(initial[t].frame)};sync();status.textContent='已导入';
 }catch(error){status.textContent=error.message}
};
document.querySelector('#zoom').onchange=e=>canvas.style.width=e.target.value+'px';
image.onload=()=>{canvas.width=image.width;canvas.height=image.height;canvas.style.width='728px';sync()};
image.src='data:image/png;base64,__IMAGE__';
</script></html>'''


if __name__ == '__main__':
    main()
