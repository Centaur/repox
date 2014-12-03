$('.ui.checkbox').checkbox();
$('select.dropdown').dropdown();

var app = new Vue({
    el: '#app',
    data: {
        channel: 'upstream'
    }
});

var upstream = new Vue({
    el: '#upstream2',
    data: {
        upstreams: []
    },
    methods: {
        showAddRepoDialog: function (e) {
            console.log('show dialog');
            $('#addRepoDialog').modal('show')
        }
    }

});

//$.get('/config/upstreams', function (config) {
//    upstream.data.upstreams = config;
//});