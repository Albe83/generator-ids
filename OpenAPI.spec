openapi: 3.0.3
info:
  title: Unique ID Generation Service
  description: API for generating unique identifiers using the Snowflake ID algorithm.
  version: 1.0.0
servers:
  - url: https://{host}/api/v1
    description: Production server
    variables:
      host:
        default: unique-id-service.example.com
        description: |-
          The default hostname for the service. Replace this value with an appropriate hostname for non-production environments such as:
          - `staging.unique-id-service.example.com` for staging.
          - `localhost` for local testing.
paths:
  /generateUniqueIds:
    get:
      summary: Generate a unique identifier
      description: Generates a unique identifier based on the Snowflake ID algorithm.
      operationId: generateUniqueIds
      parameters:
        - name: count
          in: query
          description: Number of unique IDs to generate. Default is 1.
          required: false
          schema:
            type: integer
            minimum: 1
            maximum: 100
            default: 1
      responses:
        '200':
          description: Successfully generated unique IDs.
          content:
            application/json:
              schema:
                type: object
                properties:
                  ids:
                    type: array
                    items:
                      type: string
                    description: List of generated unique identifiers.
                    minItems: 1
                    maxItems: 100
        '400':
          description: Invalid request parameters.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
        '500':
          description: Internal server error. This error occurs when there is an unexpected condition on the server side.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'
components:
  schemas:
    ErrorResponse:
      type: object
      properties:
        code:
          type: string
          description: Error code identifying the type of error.
        message:
          type: string
          description: Human-readable description of the error.
