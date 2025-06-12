const roles = [
    { name: "Admin", privilegeLevel: 9 },
    { name: "User", privilegeLevel: 3 },
    { name: "Visitor", privilegeLevel: 1 },
    { name: "Blocked", privilegeLevel: 0 },
]

/**
 * @param {(typeof roles)[number]} role
 * @param {number} targetPrivilegeLevel
 */
function hasPrivilege(role, targetPrivilegeLevel) {
    return role.privilegeLevel >= targetPrivilegeLevel
}