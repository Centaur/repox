var repoxApp = angular.module('repoxApp', ['ngRoute', 'repoxControllers']);

repoxApp.config(['$routeProvider', function ($routeProvider) {
    $routeProvider.
        when('/upstreams', {
            templateUrl: 'partials/upstreams.html',
            controller: 'UpstreamsCtrl'
        }).
        when('/connectors', {
            templateUrl: 'partials/connectors.html',
            controller: 'ConnectorsCtrl'
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
        when('/login', {
            templateUrl: 'partials/login.html',
            controller: 'LoginCtrl'
        }).
        when('/modifyPassword', {
            templateUrl: 'partials/modifyPassword.html',
            controller: 'ModifyPwdCtrl'
        }).
        otherwise({
            redirectTo: '/login'
        })
}]);

repoxApp.filter('displayProxy', function () {
    return function (proxy) {
        if (!proxy) return 'No Proxy';
        var result = proxy.protocol.toLowerCase() + "://" + proxy.host;
        if (proxy.port && proxy.port != 80)
            result = result + ":" + proxy.port;
        if (proxy.name)
            result = result + "（" + proxy.name + "）";
        return result;
    }
});

repoxApp.filter('displayCredentials', function(){
    return function(credential) {
        return credential.scheme+':'+credential.user+':'+ credential.password.replace(/./g,'*')
    }
});

repoxApp.filter('displayParameter', function () {
    return function (parameter) {
        if (parameter.name === 'extraResources')
            return parameter.value.join(":");
        else return parameter.value;
    }
});

var repoxControllers = angular.module('repoxControllers', []);

repoxControllers.controller('MenuCtrl', ['$scope', '$location', '$http', '$route', function ($scope, $location, $http, $route) {
    function endsWith(str, suffix) {
        return str.indexOf(suffix, str.length - suffix.length) !== -1;
    }

    $scope.activeOn = function (uri) {
        if (endsWith($location.path(), uri))
            return 'active';
        else return '';
    };

    $scope.resetMainClient = function () {
        $http.post('resetMainClient', {}).success(function () {
            alert('Main client reset.')
        })
    };
    $scope.resetProxyClients = function () {
        $http.post('resetProxyClients', {}).success(function () {
            alert('Proxy clients reset.')
        })
    };
    $scope.logout = function () {
        $http.post('logout', {}).success(function () {
            $location.path('/login')
        })
    };
    $scope.modifyPassword = function () {
        $location.path('/modifyPassword')
    }
}]);

repoxControllers.controller('UpstreamsCtrl', ['$scope', '$http', '$route', 'auth', '$timeout', function ($scope, $http, $route, auth, $timeout) {
    auth(function () {
        $scope.escapeToClose = function ($event) {
            if ($event.keyCode == 27) {
                $scope.newUpstreamDialogVisible = false;
                $scope.editUpstreamDialogVisible = false;
            }
        };
        $scope.newUpstreamDialogVisible = false;
        $scope.editUpstreamDialogVisible = false;

        $scope.upstreams = [];
        $scope.connectors = [];

        $http.get('upstreams').success(function (data) {
            $scope.connectors = data.connectors;
            $scope.upstreams = _.map(data.upstreams, function (upstream) {
                if (upstream.connector) {
                    upstream.connector = _.find(data.connectors, function (el) {
                        return upstream.connector.id && el.id == upstream.connector.id
                    })
                }
                return upstream;
            });
        });
        $scope.moveUp = function (repo) {
            $http.post('upstream/up?v=' + repo.id, {}).success(function () {
                $route.reload();
            })
        };
        $scope.moveDown = function (repo) {
            $http.post('upstream/down?v=' + repo.id, {}).success(function () {
                $route.reload();
            })
        };

        $scope.deleteRepo = function (repo) {
            $http.delete('upstream?v=' + repo.id).success(function () {
                $route.reload();
            })
        };
        $scope.toggleDisable = function (repo) {
            var method = repo.disabled ? "enable" : "disable";
            $http.put('upstream/' + method + '?v=' + repo.id, {}).success(function () {
                $route.reload();
            })
        };
        $scope.toggleMaven = function (repo) {
            repo.maven = !repo.maven;
        };
        $scope.toggleGetOnly = function (repo) {
            repo.getOnly = !repo.getOnly;
        };

        $scope.showNewRepoDialog = function () {
            $scope.newUpstream = {repo: {maven: false, getOnly: false, disabled: false, priority: 10}};
            $scope.newUpstreamDialogVisible = true;
            $timeout(function () {
                $('#new-repo-name').focus();
            });
        };
        $scope.submitNewRepo = function () {
            $http.post('upstream?v=' + encodeURIComponent(JSON.stringify($scope.newUpstream)), {}).success(function () {
                $scope.newUpstreamDialogVisible = false;
                $route.reload();
            })
        };
        $scope.showEditRepoDialog = function (upstream) {
            $scope.editUpstream = {repo: _.clone(upstream.repo), connector: upstream.connector};
            $scope.editUpstreamDialogVisible = true;
            $timeout(function () {
                $('#edit-repo-name').select().focus();
            });
        };
        $scope.submitEditRepo = function () {
            $http.put('upstream?v=' + encodeURIComponent(JSON.stringify($scope.editUpstream)), {}).success(function () {
                $scope.editUpstreamDialogVisible = false;
                $route.reload();
            })
        };
    });
}]);
repoxControllers.controller('ConnectorsCtrl', ['$scope', '$http', '$route', 'auth', '$timeout', function ($scope, $http, $route, auth, $timeout) {
    auth(function () {
        $scope.escapeToClose = function ($event) {
            if ($event.keyCode == 27) {
                $scope.newConnectorDialogVisible = false;
                $scope.editConnectorDialogVisible = false;
            }
        };
        $scope.newConnectorDialogVisible = false;
        $scope.editConnectorDialogVisible = false;


        $scope.schemes = ['None', 'BASIC', 'DIGEST'];
        $scope.proxies = [];
        $scope.connectors = [];

        $http.get('connectors').success(function (data) {
            $scope.proxies = data.proxies;
            $scope.connectors = _.map(data.connectors, function (connector) {
                if (connector.proxy) {
                    connector.proxy = _.find(data.proxies, function (el) {
                        return connector.proxy.id && el.id == connector.proxy.id
                    })
                }
                return connector;
            });
        });
        $scope.showNewConnectorDialog = function () {
            $scope.newConnector = {
                connector: {
                    connectionTimeout: "6 seconds",
                    connectionIdleTimeout: "10 seconds",
                    maxConnections: 30,
                    maxConnectionsPerHost: 20,
                    scheme: 'None'
                }
            };
            $scope.newConnectorDialogVisible = true;
            $timeout(function () {
                $('#new-connector-name').focus();
            })
        };
        $scope.submitNewConnector = function () {
            var current = $scope.newConnector.connector;
            if(current.scheme !== 'None') {
                current.credentials = {
                    scheme: current.scheme,
                    user: current.user,
                    password: current.password
                }
                delete current.scheme;
                delete current.user;
                delete current.password;
            }
            $http.post('connector?v=' + encodeURIComponent(JSON.stringify($scope.newConnector)), {}).success(function () {
                $scope.newConnectorDialogVisible = false;
                $route.reload();
            })
        };
        $scope.showEditConnectorDialog = function (connectorVO) {
            var current = _.clone(connectorVO.connector);
            if(current.credentials) {
                current.user = current.credentials.user;
                current.password = current.credentials.password;
                current.scheme = current.credentials.scheme;
            } else {
                current.scheme = 'None';
            }
            delete current.credentials;
            $scope.editConnector = {connector: current, proxy: connectorVO.proxy};
            $scope.editConnectorDialogVisible = true;
            $timeout(function () {
                $('#edit-connector-name').select().focus();
            });
        };
        $scope.submitEditConnector = function () {
            var current = $scope.editConnector.connector;
            if(current.scheme !== 'None') {
                current.credentials = {
                    scheme: current.scheme,
                    user: current.user,
                    password: current.password
                };
                delete current.scheme;
                delete current.user;
                delete current.password;
            }
            $http.put('connector?v=' + encodeURIComponent(JSON.stringify($scope.editConnector)), {}).success(function () {
                $scope.editConnectorDialogVisible = false;
                $route.reload();
            })
        };
        $scope.deleteConnector = function (vo) {
            $http.delete('connector?v=' + vo.connector.id).success(function () {
                $route.reload();
            })
        };


    });
}]);


repoxControllers.controller('ProxiesCtrl', ['$scope', '$http', '$route', 'auth', '$timeout', function ($scope, $http, $route, auth, $timeout) {
    auth(function () {
        $scope.escapeToClose = function ($event) {
            if ($event.keyCode == 27) {
                $scope.newProxyDialogVisible = false;
                $scope.editProxyDialogVisible = false;
            }
        };
        $scope.newProxyDialogVisible = false;
        $scope.editProxyDialogVisible = false;

        $scope.proxies = [];
        $http.get('proxies').success(function (data) {
            $scope.proxies = data;
        })
        $scope.showNewProxyDialog = function () {
            $scope.newProxy = {protocol: 'HTTP', disabled: false};
            $scope.newProxyDialogVisible = true;
            $timeout(function () {
                $('#new-proxy-name').focus();
            })
        };
        $scope.submitNewProxy = function () {
            $http.post('proxy?v=' + encodeURIComponent(JSON.stringify($scope.newProxy)), {}).success(function () {
                $scope.newProxyDialogVisible = true;
                $route.reload();
            })
        };
        $scope.showEditProxyDialog = function (proxy) {
            $scope.editProxy = _.clone(proxy);
            $scope.editProxyDialogVisible = true;
            $timeout(function () {
                $('#edit-proxy-name').select().focus()
            })
        };
        $scope.submitEditProxy = function () {
            $http.put('proxy?v=' + encodeURIComponent(JSON.stringify($scope.editProxy)), {}).success(function () {
                $scope.editProxyDialogVisible = false;
                $route.reload();
            })
        }
        $scope.deleteProxy = function (proxy) {
            $http.delete('proxy?v=' + proxy.id).success(function () {
                $route.reload();
            })
        };
        $scope.toggleDisable = function (proxy) {
            var method = proxy.disabled ? "enable" : "disable";
            $http.put('proxy/' + method + '?v=' + proxy.id, {}).success(function () {
                $route.reload();
            })
        };
    });
}]);
repoxControllers.controller('Immediate404RulesCtrl', ['$scope', '$http', '$route', 'auth', '$timeout', function ($scope, $http, $route, auth, $timeout) {
    auth(function () {
        $scope.escapeToClose = function ($event) {
            if ($event.keyCode == 27) {
                $scope.new404RuleDialogVisible = false;
                $scope.edit404RuleDialogVisible = false;
            }
        };
        $scope.new404RuleDialogVisible = false;
        $scope.edit404RuleDialogVisible = false;
        $scope.rules = [];
        $http.get('immediate404Rules').success(function (data) {
            $scope.rules = data;
        });
        $scope.showNewRuleDialog = function () {
            $scope.newRule = {disabled: false};
            $scope.new404RuleDialogVisible = true;
            $timeout(function () {
                $('#new-rule-include').focus();
            })
        };
        $scope.submitNewRule = function () {
            $http.post('immediate404Rule?v=' + encodeURIComponent(JSON.stringify($scope.newRule)), {}).success(function () {
                $scope.new404RuleDialogVisible = false;
                $route.reload();
            })
        };
        $scope.showEditRuleDialog = function (rule) {
            $scope.editRule = _.clone(rule);
            $scope.edit404RuleDialogVisible = true;
            $timeout(function () {
                $('#edit-rule-include').select().focus();
            })
        };
        $scope.submitEditRule = function () {
            $http.put('immediate404Rule?v=' + encodeURIComponent(JSON.stringify($scope.editRule)), {}).success(function () {
                $scope.edit404RuleDialogVisible = false;
                $route.reload();
            })
        };
        $scope.deleteRule = function (rule) {
            $http.delete('immediate404Rule?v=' + rule.id).success(function () {
                $route.reload();
            })
        };
        $scope.toggleDisable = function (rule) {
            var method = rule.disabled ? "enable" : "disable";
            $http.put('immediate404Rule/' + method + '?v=' + rule.id, {}).success(function () {
                $route.reload();
            })
        };

    });
}]);
repoxControllers.controller('ExpireRulesCtrl', ['$scope', '$http', '$route', 'auth', '$timeout', function ($scope, $http, $route, auth, $timeout) {
    auth(function () {
        $scope.escapeToClose = function ($event) {
            if ($event.keyCode == 27) {
                $scope.newExpireRuleDialogVisible = false;
                $scope.editExpireRuleDialogVisible = false;
            }
        };
        $scope.newExpireRuleDialogVisible = false;
        $scope.editExpireRuleDialogVisible = false;

        $scope.rules = [];
        $http.get('expireRules').success(function (data) {
            $scope.rules = data;
        });
        $scope.showNewRuleDialog = function () {
            $scope.newRule = {disabled: false};
            $scope.newExpireRuleDialogVisible = true;
            $timeout(function () {
                $('#new-rule-pattern').focus();
            });
        };
        $scope.submitNewRule = function () {
            $http.post('expireRule?v=' + encodeURIComponent(JSON.stringify($scope.newRule)), {}).success(function () {
                $scope.newExpireRuleDialogVisible = false;
                $route.reload();
            })
        };
        $scope.showEditRuleDialog = function (rule) {
            $scope.editRule = _.clone(rule);
            $scope.editExpireRuleDialogVisible = true;
            $timeout(function () {
                $('#edit-rule-pattern').select().focus();
            });
        };
        $scope.submitEditRule = function () {
            $http.put('expireRule?v=' + encodeURIComponent(JSON.stringify($scope.editRule)), {}).success(function () {
                $scope.editExpireRuleDialogVisible = false;
                $route.reload();
            })
        };
        $scope.deleteRule = function (rule) {
            $http.delete('expireRule?v=' + rule.id).success(function () {
                $route.reload();
            })
        };
        $scope.toggleDisable = function (rule) {
            var method = rule.disabled ? "enable" : "disable";
            $http.put('expireRule/' + method + '?v=' + rule.id, {}).success(function () {
                $route.reload();
            })
        };
    });
}]);

repoxControllers.controller('ParametersCtrl', ['$scope', '$http', '$route', 'auth', '$timeout', function ($scope, $http, $route, auth, $timeout) {
    auth(function () {
        $scope.escapeToClose = function ($event) {
            if ($event.keyCode == 27) {
                $scope.editParameterDialogVisible = false;
            }
        };
        $scope.parameters = [];
        $http.get('parameters').success(function (data) {
            $scope.parameters = data;
        });

        $scope.showEditParameterDialog = function (parameter) {
            $scope.editParameter = _.clone(parameter);
            $scope.editParameterDialogVisible = true;
            $timeout(function () {
                $('#edit-parameter').select().focus();
            })
        };
        $scope.submitParameter = function () {
            $http.put($scope.editParameter.name + '?v=' + $scope.editParameter.value, {}).success(function () {
                $scope.editParameterDialogVisible = false;
                $route.reload();
            })
        };

    });
}]);

repoxControllers.controller('LoginCtrl', ['$scope', '$http', '$route', '$window', '$location', function ($scope, $http, $route, $window, $location) {
    $scope.password = '';
    $scope.doLogin = function () {
        $http.post('login?v=' + encodeURIComponent($scope.password), {}).success(function (resp) {
            if (resp.success) {
                $location.path('/upstreams')
            } else {
                $window.alert('Login Failed.');
                $route.reload();
            }
        })
    }
}]);

repoxControllers.controller('ModifyPwdCtrl', ['$scope', '$http', '$route', '$window', '$location', 'auth', function ($scope, $http, $route, $window, $location, auth) {
    auth(function () {
        $scope.password = '';
        $scope.submitNewPwd = function () {
            $http.put('password?v=' + encodeURIComponent(JSON.stringify({
                p1: $scope.password,
                p2: $scope.confirm
            })), {}).success(function () {
                $window.alert('Success. Relogin.');
                $location.path('/login')
            }).error(function () {
                $window.alert('Failed');
                $route.reload();
            })
        };
    });
}]);

repoxControllers.factory('auth', ['$location', function ($location) {
    function readCookie(name) {
        var nameEQ = name + "=";
        var ca = document.cookie.split(';');
        for (var i = 0; i < ca.length; i++) {
            var c = ca[i];
            while (c.charAt(0) === ' ') c = c.substring(1, c.length);
            if (c.indexOf(nameEQ) === 0) return c.substring(nameEQ.length, c.length);
        }
        return null;
    }

    return function (callback) {
        if (!(readCookie('authenticated') === 'true')) {
            $location.path('/login')
        } else {
            callback();
        }
    }
}]);