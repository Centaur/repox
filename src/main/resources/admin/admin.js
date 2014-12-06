
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
        when('/immediate404Rules', {
            templateUrl: 'partials/immediate404Rules.html',
            controller: 'Immediate404RulesCtrl'
        }).
        when('/expireRules', {
            templateUrl: 'partials/expireRules.html',
            controller: 'ExpireRulesCtrl'
        }).
        when('/parameters', {
            templateUrl: 'partials/parameters.html',
            controller: 'ParametersCtrl'
        }).
        otherwise({
            redirectTo: '/upstreams'
        })
}]);

repoxApp.filter('displayProxy', function() {
    return function (proxy) {
        var result = proxy.protocol + "://" + proxy.host;
        if (proxy.port && proxy.port != 80)
            result = result + ":" + proxy.port;
        if (proxy.name)
            result = result + "(" + proxy.name + ")";
        return result;
    }
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

repoxControllers.controller('UpstreamsCtrl', ['$scope', '$http', '$route', function ($scope, $http, $route) {
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

    $scope.toggleDisable = function (repo) {
        var method = repo.disabled ? "enable" : "disable";
        $http.put('upstream/'+method+'?v=' + repo.id, {}).success(function () {
            $route.reload();
        })
    };
}]);


repoxControllers.controller('ProxiesCtrl', ['$scope', '$http', function ($scope, $http) {
    $scope.proxies = [];
    $http.get('proxies').success(function (data) {
        $scope.proxies = data.proxies;
    })
}]);
repoxControllers.controller('Immediate404RulesCtrl', ['$scope', '$http', function ($scope, $http) {
    $scope.rules = [];
    $http.get('immediate404Rules').success(function(data){
        $scope.rules = data.rules;
    })
}]);
repoxControllers.controller('ExpireRulesCtrl', ['$scope', '$http', function ($scope, $http) {
    $scope.rules = [];
    $http.get('expireRules').success(function(data){
        $scope.rules = data.rules;
    })
}]);
repoxControllers.controller('ParametersCtrl', ['$scope', '$http', function ($scope, $http) {
    $scope.parameters = [];
    $http.get('parameters').success(function (data) {
        $scope.parameters = data.parameters;
    })
}]);
