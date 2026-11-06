// Reading follows the user's own rights in the backend, so the server asks only for the read group. Restricted data and
// the service account outside the user's scopes are refused by rules the platform generates for the declared types.

@id("example-read")
permit (
  principal in UserGroup::"mcp-example-read",
  action == Example::Action::"read",
  resource
)
when { resource is Example::Project || resource is Example::Record };
