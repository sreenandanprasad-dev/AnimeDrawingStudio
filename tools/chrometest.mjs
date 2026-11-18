import puppeteer from 'puppeteer-core';
import chromium from '@sparticuz/chromium';
const exe = await chromium.executablePath();
const browser = await puppeteer.launch({executablePath: exe, args: [...chromium.args, '--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--no-sandbox'], headless: true});
const page = await browser.newPage();
await page.setContent('<canvas id=c></canvas>');
const info = await page.evaluate(()=>{const gl=document.getElementById('c').getContext('webgl2');return gl?gl.getParameter(gl.VERSION)+' | '+gl.getParameter(gl.RENDERER):'no webgl2';});
console.log(info);
await browser.close();
