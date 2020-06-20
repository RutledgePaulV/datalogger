[![Build Status](https://travis-ci.com/rutledgepaulv/datalogger.svg?branch=master)](https://travis-ci.com/rutledgepaulv/datalogger)
[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.rutledgepaulv/datalogger.svg)](https://clojars.org/org.clojars.rutledgepaulv/datalogger)
[![codecov](https://codecov.io/gh/rutledgepaulv/datalogger/branch/master/graph/badge.svg)](https://codecov.io/gh/rutledgepaulv/datalogger)


### Rationale

I wanted a no-fuss structured logging solution for Clojure. I run all my code in containerized environments
that only need to write to `stdout` so I really don't require support for other appenders. I want to avoid the 
java logging ecosystem when I log from Clojure. On the other hand, I want all the logs, including java libraries, 
to be controlled by a single piece of logging configuration. I don't want to write code to configure logging at runtime. 
I really just want a static config file, and it might as well be edn. I want built-in support for masking sensitive values.
I want to be able to push contextual data onto the stack and have it added to any logs. I want data from java MDC to appear
in my logs. I want to easily write tests that assert my log statements and their data. I want as much work as possible to 
happen off of the thread that made the logging call.

---

### Caution

This library is me satisfying the desires listed above. I think it's great for use within an application, but I would 
strongly advise against using it in a library, especially if you plan to distribute that library to others who may want 
alternative logging strategies. As currently implemented, if datalogger is on the classpath it unabashedly claims the 
logging throne and snuffs out any contenders. Maybe when I'm less frustrated by what logging has become I'll join the
party and make my new layer assume a configurable role in the rest of your cake.

---

### Configuration

Place a datalogger.edn on the root of your classpath. That's it.

``` 
{; define the logger levels for java classes or clojure namespaces
 ; wildcards are supported to match "everything else" or "everything else within this package"
 :levels  {"*"                               :warn
           "org.eclipse.jetty.server.Server" :info
           "my-app.*"                        :info}

 ; remove this attribute from every log statement if it's present
 :elide   #{"column"}

 :masking {; mask the value contained at any of these keys
           :keys   #{:ssn}
       
           ; mask any values that match any of these regular expressions
           :values #{"\\d{3}-\\d{2}-\\d{4}"}}

 ; pretty print the json (nice for local dev but you should probably turn it off in prod)
 :mapper  {:pretty true}}
```

---

### Usage

```clojure

(log :error {:some-data true})

; => {
;      "@hostname" : "gigabyte",
;      "@thread" : "nRepl-session-7f3e5602-05dc-4891-a1aa-e1aca2cdac27",
;      "@timestamp" : "2020-06-20T05:29:16.502095Z",
;      "level" : "ERROR",
;      "line" : 1,
;      "logger" : "user",
;      "ns" : "user",
;      "some-data" : true
;    }

```


---

### License

This project is licensed under [MIT license](http://opensource.org/licenses/MIT).