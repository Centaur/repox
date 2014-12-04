module.exports = {
    template: require('./template.html'),
    data: function () {
        return {
            upstreams: []
        };
    },
    ready: function () {
        $.get('/admin/upstreams', function (resp) {
            this.upstreams = resp
        })
    },
    methods: {
        showAddRepoDialog: function () {
            $('#addRepoDialog').modal('show');
        }
    }
}