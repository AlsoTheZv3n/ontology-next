import { gql } from '@apollo/client'

export const CREATE_OBJECT = gql`
  mutation CreateObject($objectType: String!, $properties: JSON!) {
    createObject(objectType: $objectType, properties: $properties) {
      id
      objectType
      properties
      createdAt
    }
  }
`

export const DELETE_OBJECT = gql`
  mutation DeleteObject($objectType: String!, $objectId: ID!) {
    deleteObject(objectType: $objectType, objectId: $objectId)
  }
`

export const EXECUTE_ACTION = gql`
  mutation ExecuteAction($actionType: String!, $objectId: ID, $params: JSON!) {
    executeAction(actionType: $actionType, objectId: $objectId, params: $params) {
      success
      objectId
      message
      auditLogId
    }
  }
`

export const REGISTER_ACTION_TYPE = gql`
  mutation RegisterActionType($input: RegisterActionTypeInput!) {
    registerActionType(input: $input) {
      id
      apiName
      displayName
      targetObjectType
      requiresApproval
    }
  }
`

export const AGENT_CHAT = gql`
  mutation AgentChat($message: String!) {
    agentChat(message: $message) {
      reply
      context
    }
  }
`
