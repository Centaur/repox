$('.ui.checkbox').checkbox();
$('select.dropdown').dropdown();

var Vue = require('vue');

var app = new Vue({
    el: '#app',
    data: {
        channel: 'upstream'
    },
    components: {
        upstream: require('./upstream')
    }
});


//$.get('/config/upstreams', function (config) {
//    upstream.data.upstreams = config;
//});