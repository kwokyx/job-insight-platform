export const ROLE = {
  STUDENT: 0,
  ADMIN: 1,
  TEACHER: 2
}

const ROLE_ALIAS_MAP = {
  0: ROLE.STUDENT,
  1: ROLE.ADMIN,
  2: ROLE.TEACHER,
  student: ROLE.STUDENT,
  user: ROLE.STUDENT,
  admin: ROLE.ADMIN,
  administrator: ROLE.ADMIN,
  teacher: ROLE.TEACHER
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
  const currentRole = normalizeRoleType(user?.roleType)
  return allowedRoles.map(normalizeRoleType).includes(currentRole)
}

export function normalizeRoleType(roleType) {
  if (roleType === null || roleType === undefined || roleType === '') {
    return ROLE.STUDENT
  }

  const normalizedKey = String(roleType).trim().toLowerCase()
  if (normalizedKey in ROLE_ALIAS_MAP) {
    return ROLE_ALIAS_MAP[normalizedKey]
  }

  const parsed = Number(roleType)
  return Number.isNaN(parsed) ? ROLE.STUDENT : parsed
}
