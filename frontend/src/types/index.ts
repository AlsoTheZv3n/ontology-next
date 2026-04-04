export interface ObjectTypeSchema {
  id: string
  apiName: string
  displayName: string
  description?: string
  properties: PropertyTypeSchema[]
  linkTypes: LinkTypeSchema[]
}

export interface PropertyTypeSchema {
  apiName: string
  displayName: string
  dataType: string
  isPrimaryKey: boolean
  isRequired: boolean
}

export interface LinkTypeSchema {
  apiName: string
  displayName: string
  targetObjectType: string
  cardinality: string
}

export interface OntologyObject {
  id: string
  objectType: string
  properties: Record<string, unknown>
  createdAt: string
  updatedAt?: string
}

export interface ObjectPage {
  items: OntologyObject[]
  totalCount: number
  hasNextPage: boolean
  cursor?: string
}

export interface ActionLogEntry {
  id: string
  actionType: string
  objectId?: string
  performedBy: string
  status: string
  beforeState?: Record<string, unknown>
  afterState?: Record<string, unknown>
  performedAt: string
}

export interface ActionResult {
  success: boolean
  objectId?: string
  message?: string
  auditLogId: string
}

export interface DataSource {
  id: string
  apiName: string
  displayName: string
  connectorType: string
  config: string
  objectTypeId: string
  columnMapping: string
  syncIntervalCron: string
  isActive: boolean
  lastSyncedAt?: string
  createdAt: string
}

export interface SyncResultLog {
  id: string
  dataSourceId: string
  status: string
  objectsSynced: number
  objectsCreated: number
  objectsUpdated: number
  objectsFailed: number
  errorMessage?: string
  startedAt: string
  finishedAt?: string
}
