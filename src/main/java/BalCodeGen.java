import io.ballerina.compiler.syntax.tree.FunctionBodyBlockNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.ImportDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModuleMemberDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeFactory;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.NodeParser;
import io.ballerina.compiler.syntax.tree.ServiceDeclarationNode;
import io.ballerina.compiler.syntax.tree.StatementNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import io.ballerina.tools.text.TextDocuments;
import org.ballerinalang.formatter.core.Formatter;
import org.ballerinalang.formatter.core.FormatterException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BalCodeGen {
    private final BallerinaModel ballerinaModel;


    public BalCodeGen(BallerinaModel ballerinaModel) {
        this.ballerinaModel = ballerinaModel;
    }

    public void generateBalCode() {
        System.out.println("Generating Ballerina code...");
//        System.out.println("Default Package: " + ballerinaModel.defaultPackage());
        List<ImportDeclarationNode> imports = new ArrayList<>();
        List<ModuleMemberDeclarationNode> moduleMembers = new ArrayList<>();
        for (BallerinaModel.Module module : ballerinaModel.modules()) {
            for (BallerinaModel.Import importDeclaration : module.imports()) {
                ImportDeclarationNode importDeclarationNode = NodeParser.parseImportDeclaration(
                        String.format("import %s/%s;", importDeclaration.org(), importDeclaration.module()));
                imports.add(importDeclarationNode);
            }

            for (BallerinaModel.Service service : module.services()) {
                StringBuilder stringBuilder = new StringBuilder();
                Iterator<BallerinaModel.Listener> iterator = service.listeners().iterator();
                while (iterator.hasNext()) {
                    BallerinaModel.Listener listener = iterator.next();
                    ModuleMemberDeclarationNode member = NodeParser.parseModuleMemberDeclaration(
                            String.format("listener http:Listener %s = new (%s);", listener.name(), listener.port()));
                    moduleMembers.add(member);
                    stringBuilder.append(listener.name());
                    if (iterator.hasNext()) {
                        stringBuilder.append(", ");
                    }
                }

                ServiceDeclarationNode serviceDecl = (ServiceDeclarationNode) NodeParser.parseModuleMemberDeclaration(
                        String.format("service %s on %s { }", service.basePath(), stringBuilder));


                List<Node> members = new ArrayList<>();
                for (BallerinaModel.Resource resource : service.resources()) {
                    FunctionDefinitionNode resourceMethod = (FunctionDefinitionNode) NodeParser.parseObjectMember(String.format("resource " +
                                    "function %s %s() " +
                                    "returns %s {}",
                            resource.resourceMethodName(), resource.path(), resource.returnType()));
                    List<StatementNode> bodyStmts = new ArrayList<>();

                    List<String> strList = resource.body().stream().map(BallerinaModel.BodyStatement::stmt).toList();
                    String join = String.join("", strList);
                    FunctionBodyBlockNode funcBodyBlock = NodeParser.parseFunctionBodyBlock(String.format("{ %s }", join));
                    resourceMethod = resourceMethod.modify().withFunctionBody(funcBodyBlock).apply();
                    members.add(resourceMethod);
                }
                NodeList<Node> nodeList = NodeFactory.createNodeList(members);
                serviceDecl = serviceDecl.modify().withMembers(nodeList).apply();
                moduleMembers.add(serviceDecl);
            }

//            System.out.println("Module Name: " + module.moduleName());
//            System.out.println("Imports: " + module.imports());
//            System.out.println("Variables: " + module.variables());
//            System.out.println("Services: " + module.services());
        }

        NodeList<ImportDeclarationNode> importDecls = NodeFactory.createNodeList(imports);
        NodeList<ModuleMemberDeclarationNode> moduleMemberDecls = NodeFactory.createNodeList(moduleMembers);
        ModulePartNode modulePartNode = NodeFactory.createModulePartNode(
                importDecls,
                moduleMemberDecls,
                NodeFactory.createToken(SyntaxKind.EOF_TOKEN)
        );

        SyntaxTree syntaxTree = SyntaxTree.from(TextDocuments.from(""));
        syntaxTree = syntaxTree.modifyWith(modulePartNode);
        SyntaxTree formattedSyntaxTree;
        try {
            formattedSyntaxTree = Formatter.format(syntaxTree);
        } catch (FormatterException e) {
            throw new RuntimeException(e);
        }

        System.out.println("============================================");
        System.out.println(formattedSyntaxTree.toSourceCode());
        System.out.println("============================================");
    }
}