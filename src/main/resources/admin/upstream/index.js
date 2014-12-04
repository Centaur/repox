module.exports = {
    template: require('./template.html'),
    data: function () {
        return {
            upstreams: []
        };
    },
    created: function () {
        $.get('/admin/upstreams', function (resp) {
            console.log(resp);
            this.upstreams = resp
        })
    },
    methods: {
        showAddRepoDialog: function () {
            $('#addRepoDialog').modal('show');
        }
    }
}