## Background

Extract environment variables from 1password.   

## Installation

[Install the 1password cli](https://support.1password.com/command-line-getting-started/)

```
npm install -g @slimslenderslacks/op-scripts
```

You should be able to signin to 1password using `op signin --help` and after the first time you've successfully logged in, 
there's an easier form that you can use to setup a 1password session in the current environmet:

```
eval $(op signin atomist)
```

As long as you're logged in, you can `export_secrets` will write stdout `export` statements with data from 1password

```
20:49 $ export_secrets
export CLOJARS_USERNAME=xxxxx
export CLOJARS_PASSWORD=xxxxxxxx
```

So you can use `eval $(export_secrets)` to setup a local bash environment with some secret data.

## Configuration

You can map data from 1password into the `export` statements using an `.edn` file at `~/.export-1password`.

```
{"Clojars" {"CLOJARS_USERNAME" "username"
            "CLOJARS_PASSWORD" "password"}}
```

The keys are named items in the 1password atomist "Team" vault.  The values are maps that setup the `export` statements 
(keys are the name of the environment variables, and the values are fields in the 1password secrets).
