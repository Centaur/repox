
var repoxApp = angular.module('repoxApp', ['ngRoute', 'repoxControllers']);

repoxApp.config(['$routeProvider', function ($routeProvider) {
    $routeProvider.
        when('/upstreams', {
            templateUrl: 'partials/upstreams.html',
            controller: 'UpstreamsCtrl'
        }).
        when('/proxies', {
            templateUrl: 'partials/proxies.html',
            controller: 'ProxiesCtrl'
        }).
        when('/immediate404', {
            templateUrl: 'partials/immediate404.html',
            controller: 'Immedaite404Ctrl'
        }).
        when('/expire', {
            templateUrl: 'partials/expire.html',
            controller: 'ExpireCtrl'
        }).
        when('/parameters', {
            templateUrl: 'partials/parameters.html',
            controller: 'ParametersCtrl'
        }).
        otherwise({
            redirectTo: '/upstreams'
        })
}]);

repoxApp.filter('displayProxy', function (proxy) {
    var result = proxy.protocol + "://" + proxy.host;
    if (proxy.port && proxy.port != 80)
        result = result + ":" + proxy.port;
    if (proxy.name)
        result = result + "(" + proxy.name + ")";
    return result;
});

var repoxControllers = angular.module('repoxControllers', []);

repoxControllers.controller('MenuCtrl', ['$scope', '$location', function ($scope, $location) {
    function endsWith(str, suffix) {
        return str.indexOf(suffix, str.length - suffix.length) !== -1;
    }

    $scope.activeOn = function (uri) {
        if (endsWith($location.path(), uri))
            return 'active';
        else return '';
    }
}]);

repoxControllers.controller('UpstreamsCtrl', ['$scope', '$http', function ($scope, $http) {
    $scope.upstreams = [];
    $scope.proxies = [];
    $scope.selectedProxy = {};

    $http.get('upstreams').success(function (data) {
        $scope.proxies = data.proxies;
        $scope.upstreams = _.map(data.upstreams, function (upstream) {
            if (upstream.proxy) {
                upstream.proxy = _.find(data.proxies, function (el) {
                    return upstream.proxy.id && el.id == upstream.proxy.id
                })
            }
            return upstream;
        });
    });
}]);


repoxControllers.controller('ProxiesCtrl', ['$scope', '$http', function ($scope, $http) {
}]);
repoxControllers.controller('Immediate404Ctrl', ['$scope', '$http', function ($scope, $http) {
}]);
repoxControllers.controller('ExpireCtrl', ['$scope', '$http', function ($scope, $http) {
}]);
repoxControllers.controller('ParametersCtrl', ['$scope', '$http', function ($scope, $http) {
}]);
