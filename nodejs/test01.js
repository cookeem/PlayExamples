/**
 * Created by cookeem on 16/6/23.
 */
'use strict';

let fs = require("fs");
let data = fs.readFileSync('server.js', { encoding: 'UTF-8', flag: 'r' });

console.log(data.toString());
console.log("程序执行结束!");