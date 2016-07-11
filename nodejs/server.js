/**
 * Created by cookeem on 16/6/23.
 */
'use strict';
var http = require('http');
http.createServer(function (request, response) {
    response.writeHead(200, {'Content-Type': 'application/json'});
    let firstname = 'zeng';
    let lastname = 'haijian';
    let person = {
        firstname: '曾😀haha',
        lastname: '海剑😍'
    };
    response.end(JSON.stringify(person));
}).listen(8124);
var person = {
    name: "张三"
};

console.log('Server running at http://127.0.0.1:8124/');
Storage