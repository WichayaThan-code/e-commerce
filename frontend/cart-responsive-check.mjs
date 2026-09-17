import { chromium } from 'playwright-core';
const browser = await chromium.launch({executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
try {
 const page = await browser.newPage();
 await page.goto('http://localhost:5173/cart');
 for(const width of [320,360,390,440,600,780,781,900,1000,1050,1051,1280,1440]) {
  await page.setViewportSize({width,height:956});
  for(const selected of [true,false]) {
   const checkbox=page.locator('.cart-select-row input');
   await checkbox.setChecked(selected);
   const result=await page.evaluate(()=>{
    const row=document.querySelector('.cart-list article').getBoundingClientRect();
    const copy=document.querySelector('.cart-item-copy').getBoundingClientRect();
    const quantity=document.querySelector('.cart-quantity').getBoundingClientRect();
    return {overflow:document.documentElement.scrollWidth>innerWidth,copyWidth:Math.round(copy.width),rightGap:Math.round(row.right-quantity.right),columns:getComputedStyle(document.querySelector('.cart-list article')).gridTemplateColumns};
   });
   console.log(JSON.stringify({width,selected,...result}));
   if(result.overflow || result.copyWidth<65) throw Error('Responsive layout failure');
  }
 }
 await page.setViewportSize({width:440,height:956});
 await page.screenshot({path:'/tmp/cart-mobile.png',fullPage:true});
} finally { await browser.close(); }
