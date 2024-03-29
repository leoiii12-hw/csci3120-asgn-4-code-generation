import myparser.MiniJavaParser;
import myparser.ParseException;
import syntaxtree.Program;
import visitor.BuildSymbolTableVisitor;
import visitor.CodeGenVisitor;
import visitor.TypeCheckVisitor;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class Main {
  public static void main(String[] args) {
    try {
      Program root = new MiniJavaParser(System.in).Goal();

      // Build the symbol table
      BuildSymbolTableVisitor buildSymTab = new BuildSymbolTableVisitor();
      root.accept(buildSymTab);

      // Type check
      TypeCheckVisitor typeCheck = new TypeCheckVisitor(buildSymTab.getSymTab());
      root.accept(typeCheck);

      PrintWriter out = new PrintWriter(System.out);
      if (args.length >= 1)
        out = new PrintWriter(new BufferedWriter(new FileWriter(args[0])));

      CodeGenVisitor cgen = new CodeGenVisitor(buildSymTab.getSymTab(), out);

      root.accept(cgen);

      out.close();
    } catch (ParseException e) {
      System.out.println(e.toString());
    } catch (IOException e) {
      System.out.println(e.toString());
    }
  }
}
