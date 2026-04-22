export const ROLE = {
  STUDENT: 0,
  ADMIN: 1,
  TEACHER: 2
}

export function normalizeRoleType(roleType) {
  const normalized = Number(roleType)
  return Number.isNaN(normalized) ? ROLE.STUDENT : normalized
}

export function getRoleLabel(roleType) {
  switch (normalizeRoleType(roleType)) {
    case ROLE.ADMIN:
      return '管理员'
    case ROLE.TEACHER:
      return '教师'
    case ROLE.STUDENT:
    default:
      return '学生'
  }
}

export function hasRequiredRole(user, allowedRoles = []) {
  if (!allowedRoles.length) return true
  return allowedRoles.includes(normalizeRoleType(user?.roleType))
}
