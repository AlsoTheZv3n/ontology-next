import { gql } from '@apollo/client'

export const GET_ALL_OBJECT_TYPES = gql`
  query GetAllObjectTypes {
    getAllObjectTypes {
      id
      apiName
      displayName
      description
      properties {
        apiName
        displayName
        dataType
        isPrimaryKey
        isRequired
      }
      linkTypes {
        apiName
        displayName
        targetObjectType
        cardinality
      }
    }
  }
`

export const SEARCH_OBJECTS = gql`
  query SearchObjects($objectType: String!, $pagination: PaginationInput) {
    searchObjects(objectType: $objectType, pagination: $pagination) {
      items {
        id
        objectType
        properties
        createdAt
      }
      totalCount
      hasNextPage
      cursor
    }
  }
`

export const GET_OBJECT = gql`
  query GetObject($id: ID!) {
    getObject(id: $id) {
      id
      objectType
      properties
      createdAt
      updatedAt
    }
  }
`

export const GET_LINK_TYPES = gql`
  query GetLinkTypes($sourceObjectType: String) {
    getLinkTypes(sourceObjectType: $sourceObjectType) {
      apiName
      displayName
      targetObjectType
      cardinality
    }
  }
`

export const GET_ACTION_LOG = gql`
  query GetActionLog($objectId: ID, $limit: Int) {
    getActionLog(objectId: $objectId, limit: $limit) {
      id
      actionType
      objectId
      performedBy
      status
      beforeState
      afterState
      performedAt
    }
  }
`

export const GET_ACTION_TYPES = gql`
  query GetActionTypes($objectType: String) {
    getActionTypes(objectType: $objectType) {
      id
      apiName
      displayName
      targetObjectType
      requiresApproval
      validationRules
    }
  }
`
