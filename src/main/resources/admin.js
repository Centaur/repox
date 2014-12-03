$('.ui.checkbox').checkbox();
$('select.dropdown').dropdown();

var app = new Vue({
    el: '#app',
    data: {
        channel: 'upstream'
    },
    methods: {
        xyz: function (e) {
            console.log('abc');
        }/*,
         showAddRepoDialog: function (e) {
         console.log('show dialog');
         $('#addRepoDialog').modal('show')
         }*/
    }
});

var upstream = new Vue({
    el: '#upstream2',
    data: {
        upstreams: []
    }
});

//$.get('/config/upstreams', function (config) {
//    upstream.data.upstreams = config;
//});