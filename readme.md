[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.rutledgepaulv/datalogger.svg)](https://clojars.org/org.clojars.rutledgepaulv/datalogger)

### Rationale

I want a no-fuss structured logging solution for Clojure. I run all my code in containerized environments
that only need to write to `stdout` so I really don't require support for other appenders. I want to avoid the entire
java logging ecosystem when I log from Clojure. I want all the logs, including java libraries, to be controlled 
by a single piece of logging configuration. I don't want to write code to configure logging at runtime. I really 
just want a static config file, and it might as well be edn. I want built-in support for masking sensitive values.
I want to be able to push contextual data onto the stack and have it added to any logs. I want data from java MDC to appear
in my logs. I want support for writing tests that assert my log statements and their data. I want as much as possible to 
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

Place a datalogger.edn on the root of your classpath. That's it. Bundled adapters will route all
java logging libraries through datalogger and be subject to its configuration.

```clojure 
 
{; define the logger levels for java classes or clojure namespaces
 ; wildcards are supported to match "everything else" or "everything else within this package"
 :levels  {"*"                               :warn
           "org.eclipse.jetty.server.Server" :info
           "my-app.*"                        :info}

 ; remove these attributes from every log statement when present
 :elide   #{"column"}

 ; defaults to stderr to better support CLI tools, but you can change to stdout
 :stream       :stderr
 
 :masking {; mask the value contained at any of these keys
           :keys   #{:ssn}
       
           ; mask any values that match any of these regular expressions
           :values #{"\\d{3}-\\d{2}-\\d{4}"}}

 ; pretty print the json (nice for local dev, but you should turn it off in prod)
 :json-options  {:indent true}}

```

---

### Usage

Log a message!

```clojure

(log :error "A message")

```

```json

{
  "@hostname" : "gigabyte",
  "@thread" : "nRepl-session-7f3e5602-05dc-4891-a1aa-e1aca2cdac27",
  "@timestamp" : "2020-06-20T05:29:16.502095Z",
  "level" : "ERROR",
  "line" : 1,
  "logger" : "user",
  "ns" : "user",
  "message" : "A message"
}

```

---

Log a data map with whatever you want!
 
```clojure

(log :error {:some-data true})

```

```json

{
  "@hostname" : "gigabyte",
  "@thread" : "nRepl-session-7f3e5602-05dc-4891-a1aa-e1aca2cdac27",
  "@timestamp" : "2020-06-20T05:29:16.502095Z",
  "level" : "ERROR",
  "line" : 1,
  "logger" : "user",
  "ns" : "user",
  "some-data" : true
}

```
---

Add a message that interpolates your data!

```clojure

(log :error "A message with a {value}" {:value 1})

```

```json 

{
  "@hostname" : "gigabyte",
  "@thread" : "nRepl-session-7f3e5602-05dc-4891-a1aa-e1aca2cdac27",
  "@timestamp" : "2020-06-20T05:29:16.502095Z",
  "level" : "ERROR",
  "line" : 1,
  "logger" : "user",
  "ns" : "user",
  "value" : 1,
  "message" : "A message with a 1"
}

```

---


Use a custom logger!

```clojure 

(log ["my-logger" :error] "A message")

```

```json 

{
  "@hostname" : "gigabyte",
  "@thread" : "nRepl-session-7f3e5602-05dc-4891-a1aa-e1aca2cdac27",
  "@timestamp" : "2020-06-20T05:29:16.502095Z",
  "level" : "ERROR",
  "line" : 1,
  "logger" : "my-logger",
  "ns" : "user",
  "message" : "A message"
}

```

---

Log an exception!

```clojure 

(log :error (ex-info "Boom!" {}))

```

```json 

{
  "@hostname" : "gigabyte",
  "@thread" : "nRepl-session-7f3e5602-05dc-4891-a1aa-e1aca2cdac27",
  "@timestamp" : "2020-06-20T05:29:16.502095Z",
  "level" : "ERROR",
  "line" : 1,
  "logger" : "my-logger",
  "ns" : "user",
  "exception" : {
       "message": "Boom!",
       "trace": [
            {
              "class" : "datalogger.core$eval3223",
              "filename" : "form-init1437998932311614649.clj",
              "line" : 1,
              "method" : "invokeStatic"
            }
       ],
       "data": {}
   }
}

```

---

Supply arguments in any combination and in any order!

```clojure 

(log "A message {value}" {:value 1} (ex-info "Test" {}) ["custom-logger" :error])

```

```json 

{
  "@hostname" : "gigabyte",
  "@thread" : "nRepl-session-7f3e5602-05dc-4891-a1aa-e1aca2cdac27",
  "@timestamp" : "2020-06-20T05:29:16.502095Z",
  "level" : "ERROR",
  "line" : 1,
  "logger" : "custom-logger",
  "ns" : "user",
  "value" : 1,
  "message": "A message 1",
  "exception" : {
       "message": "Test",
       "trace": [
            {
              "class" : "datalogger.core$eval3223",
              "filename" : "form-init1437998932311614649.clj",
              "line" : 1,
              "method" : "invokeStatic"
            }
       ],
       "data": {}
   }
}

```

---

Add context to the stack. Each push onto the stack deeply merges with what's already there.

```clojure 

(with-context {:outside 1}
  (with-context {:inside 2}
    (log :error "Demonstration.")))

```

```json 
{
  "@hostname" : "gigabyte",
  "@thread" : "nRepl-session-463f63ea-d077-4d97-8099-9f4a37c4fca3",
  "@timestamp" : "2020-06-20T13:41:01.572Z",
  "inside" : 2,
  "level" : "ERROR",
  "line" : 3,
  "logger" : "datalogger.core",
  "message" : "Demonstration.",
  "ns" : "datalogger.core",
  "outside" : 1
}
```

---

Capture the logs that get written (still prints to stdout too).

```clojure

(capture
  (log :error "Test")
  (log :error "Toast")
  "return-value")

; a tuple!

[
 ; a vector of parsed log data from logs that were written.
 [{"@timestamp" "2020-06-20T13:24:45.042Z",
   "@thread"    "nRepl-session-b9e56f42-4cba-41c9-9632-92da63c93c99",
   "@hostname"  "gigabyte",
   "ns"         "datalogger.core",
   "level"      "ERROR",
   "line"       3,
   "logger"     "datalogger.core",
   "message"    "Test"}
  {"@timestamp" "2020-06-20T13:24:45.042Z",
   "@thread"    "nRepl-session-b9e56f42-4cba-41c9-9632-92da63c93c99",
   "ns"         "datalogger.core",
   "@hostname"  "gigabyte",
   "level"      "ERROR",
   "line"       4,
   "logger"     "datalogger.core",
   "message"    "Toast"}]

 ; return value of body passed to capture
 "return-value"]

```

---

### License

This project is licensed under [MIT license](http://opensource.org/licenses/MIT).