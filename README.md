![](https://turbomates.com/wp-content/uploads/2019/11/logo-e1573642672476.png)
[![Project Status: WIP – Initial development is in progress, but there has not yet been a stable, usable release suitable for the public.](https://www.repostatus.org/badges/latest/wip.svg)](https://www.repostatus.org/#wip)
# Ktor open API
Creates OpenApi documentation based on Ktor routing and response return types

# Usage
```kotlin
post<Response.Either<Response.Data<UUID>, Response.Errors>, RegisterUser>("/register") { command ->
    // ...
    command.locale = this.call.resolveLocale()
    controller<UserController>(this).register(command)
}

get<Response.Data<Preferences>>("/preferences") {
    // ...
}
```

## Commands
See `Makefile`
