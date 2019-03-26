package com.github.manevolent.jbot.command.builtin;

import com.github.manevolent.jbot.command.CommandSender;
import com.github.manevolent.jbot.command.exception.CommandArgumentException;
import com.github.manevolent.jbot.command.exception.CommandExecutionException;
import com.github.manevolent.jbot.command.executor.chained.AnnotatedCommandExecutor;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentLabel;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentPage;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentString;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentSwitch;
import com.github.manevolent.jbot.conversation.Conversation;
import com.github.manevolent.jbot.conversation.ConversationProvider;
import com.github.manevolent.jbot.entity.Entity;
import com.github.manevolent.jbot.security.Grant;
import com.github.manevolent.jbot.security.GrantedPermission;
import com.github.manevolent.jbot.security.Permission;
import com.github.manevolent.jbot.user.User;
import com.github.manevolent.jbot.user.UserGroup;
import com.github.manevolent.jbot.user.UserManager;

import java.util.ArrayList;
import java.util.function.Function;

public class PermissionCommand extends AnnotatedCommandExecutor {
    private final UserManager userManager;
    private final ConversationProvider conversationProvider;

    public PermissionCommand(UserManager userManager, ConversationProvider conversationProvider) {
        this.userManager = userManager;
        this.conversationProvider = conversationProvider;
    }

    @Override
    public String getDescription() {
        return "Manages permissions and security";
    }

    @Command(description = "Lists permissions of an entity", permission = "system.permission.list")
    public void list(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "list") String list,
                     @CommandArgumentSwitch.Argument(labels = {"user","group","conversation"}) String entityType,
                     @CommandArgumentString.Argument(label = "entity name") String entityName,
                     @CommandArgumentPage.Argument int page)
            throws CommandExecutionException {
        Function<String, Entity>  entityFunction = createEntityAccessor(entityType);

        Entity entity = entityFunction.apply(entityName);

        sender.list(
                GrantedPermission.class,
                builder -> builder
                        .direct(new ArrayList<>(entity.getPermissions()))
                        .page(page)
                        .responder((sender1, permission) ->
                                permission.getGrant().name().toLowerCase() + " " + permission.getPermission().toString()
                                        + " (granted by " + permission.getGranter().getDisplayName() +
                                        " on " + permission.getDate() + ")")
                        .build()
        ).send();
    }

    @Command(description = "Adds permissions to an entity", permission = "system.permission.add")
    public void add(CommandSender sender,
                    @CommandArgumentLabel.Argument(label = "add") String list,
                    @CommandArgumentSwitch.Argument(labels = {"user","group","conversation"}) String entityType,
                    @CommandArgumentString.Argument(label = "entity name") String entityName,
                    @CommandArgumentString.Argument(label = "permission node") String node)
            throws CommandExecutionException {
        add(sender, "add", entityType, entityName, node, Grant.ALLOW.name().toLowerCase());
    }


    @Command(description = "Adds permissions to an entity", permission = "system.permission.add")
    public void add(CommandSender sender,
                    @CommandArgumentLabel.Argument(label = "add") String add,
                    @CommandArgumentSwitch.Argument(labels = {"user","group","conversation"}) String entityType,
                    @CommandArgumentString.Argument(label = "entity name") String entityName,
                    @CommandArgumentString.Argument(label = "permission node") String node,
                    @CommandArgumentSwitch.Argument(labels = {"allow", "deny"}) String grant)
            throws CommandExecutionException {
        Function<String, Entity>  entityFunction = createEntityAccessor(entityType);
        Entity entity = entityFunction.apply(entityName);
        if (entity.getPermission(node) != null)
            throw new CommandArgumentException("Permission already granted to entity.");
        node = node.toLowerCase();
        Grant g = grant.equalsIgnoreCase(Grant.ALLOW.name()) ? Grant.ALLOW : Grant.DENY;
        entity.setPermission(node, g);
        sender.sendMessage("Granted \"" + node + "\" to entity (" + g.name().toLowerCase() + ")");
    }


    @Command(description = "Removes permissions from an entity", permission = "system.permission.remove")
    public void remove(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "remove") String remove,
                       @CommandArgumentSwitch.Argument(labels = {"user","group","conversation"}) String entityType,
                       @CommandArgumentString.Argument(label = "entity name") String entityName,
                       @CommandArgumentString.Argument(label = "permission node") String node)
            throws CommandExecutionException {
        Permission.checkPermission(node);
        Function<String, Entity>  entityFunction = createEntityAccessor(entityType);
        Entity entity = entityFunction.apply(entityName);
        if (entity.getPermission(node) == null)
            throw new CommandArgumentException("Permission not granted to entity.");
        node = node.toLowerCase();
        entity.removePermission(node);
        sender.sendMessage("Removed \"" + node + "\" from entity.");
    }


    @Command(description = "Tests a permission on your user")
    public void test(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "test") String test,
                     @CommandArgumentString.Argument(label = "node") String node) {
        Permission.checkPermission(node);
        sender.sendMessage("Permission was allowed.");
    }

    private Function<String, Entity> createEntityAccessor(String type) {
        switch (type.toLowerCase()) {
            case "user":
                return entityName -> {
                    User user = userManager.getUserByDisplayName(entityName);
                    if (user == null) throw new IllegalArgumentException("User not found.");
                    return user.getEntity();
                };
            case "group":
                return entityName -> {
                    UserGroup group = userManager.getUserGroupByName(entityName);
                    if (group == null) throw new IllegalArgumentException("User group not found.");
                    return group.getEntity();
                };
            case "conversation":
                return entityName -> {
                    Conversation conversation = conversationProvider.getConversationById(entityName);
                    if (conversation == null) throw new IllegalArgumentException("Conversation not found.");
                    return conversation.getEntity();
                };
            default:
                throw new IllegalArgumentException("Unknown entity type");
        }
    }

}