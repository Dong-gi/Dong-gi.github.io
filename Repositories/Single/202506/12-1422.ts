const roles = ["Admin", "User", "Visitor", "Blocked"] as const


type RoleName = (typeof roles)[number]