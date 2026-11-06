// A change needs the write group and a scope that covers the record. `principal in resource.scopes` is true when one of
// the user's groups is mapped to a scope of the resource (the administrator's scope mapping, not a group name).

@id("example-update-record-in-scope")
permit (
  principal in UserGroup::"mcp-example-write",
  action == Example::Action::"update",
  resource is Example::Record
)
when { principal in resource.scopes };
