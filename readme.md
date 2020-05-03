[![Build Status](https://travis-ci.com/rutledgepaulv/datalogger.svg?branch=master)](https://travis-ci.com/rutledgepaulv/datalogger)
[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.rutledgepaulv/datalogger.svg)](https://clojars.org/org.clojars.rutledgepaulv/datalogger)
[![codecov](https://codecov.io/gh/rutledgepaulv/datalogger/branch/master/graph/badge.svg)](https://codecov.io/gh/rutledgepaulv/datalogger)


### Rationale


- The Java logging ecosystem is a broken plate that's been haphazardly glued back together.
- The community is beginning to realize there's great value in logging structured data, not text.
- It's far from feasible to move the Java ecosystem towards a new standard. 
- Containers have replaced application servers and aggregating logs has become an infrastructure service.
- clojure.tools.logging routes your clojure logging right through the mess instead of around it.
- timbre is a macro-laden kitchen sink that you have to configure at runtime.

---

### Resolution

I offer a new logging library for Clojure applications. It is opinionated and restrained.

- It will route all java logging libraries through itself without requiring any configuration.
- It will only support output as structured data, serialized as json.
- It will perform all masking / serializing / output on a background thread.
- It will not support runtime configuration and instead use an edn config file on the classpath. 
- It will not support pluggable appenders and will only ever write to standard out and standard error.
- It will provide excellent test facilities for asserting log statements made in your code.
- It will offer good, though perhaps not great, performance. 
- It will bypass any and all java logging libraries when you log from Clojure. 
- It will include any available MDC data in the logs. 
- It will provide stack-based logging contexts for Clojure that support rich data (not only key/value strings like MDC).
- It will support filtering for accidental sensitive data.
- It will namespace attributes to ensure consistent data types for a given key (important if you're later ingesting into elasticsearch).
- It will accept your arguments in any order because who can remember that.
- It will use a simple string template language that support accessing values in structured data.
- It will allow you to provide delayed values for things you don't want to compute unless a log level is enabled.

---

### License

This project is licensed under [MIT license](http://opensource.org/licenses/MIT).