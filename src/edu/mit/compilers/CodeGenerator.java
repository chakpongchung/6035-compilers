package edu.mit.compilers;

import edu.mit.compilers.cfg.CFG;
import edu.mit.compilers.cfg.GlobalCP;
import edu.mit.compilers.cfg.GlobalCSE;
import edu.mit.compilers.cfg.GlobalURE;
import edu.mit.compilers.cfg.*;
import edu.mit.compilers.ir.IrProgram;
import edu.mit.compilers.ll.*;
import edu.mit.compilers.opt.RegisterAllocation;
import edu.mit.compilers.parallel.Parallelize;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Created by abel on 10/22/16.
 */
public class CodeGenerator {

    // get the High Level Ir Program
    public String generateCode(IrProgram program, ArrayList<String> options){
        String assembled = "";
        LlBuildersList buildersList = program.getLlBuilders();

        ArrayList<String> givenRegisters = new ArrayList<>();
        HashMap<String, ArrayList<LlLocationVar>> methodParams = program.getMethodArgs();

        String registers[] = {"%r12", "%r13", "%r14", "%r15", "%rbx"};
        String empty[] = {};





        AssemblyBuilder assemblyBuilder = new AssemblyBuilder();
        this.appendGlobalVarsAndArrays(assemblyBuilder, buildersList);
        assemblyBuilder.addLine(".globl main");
        assemblyBuilder.addLine();

        // keeps track of what strings are created and
        int countSymbolTables = 0;
        for(LlSymbolTable table : buildersList.getSymbolTables()){
            for (LlComponent component : table.getLlStringTable().keySet()){

                if(component instanceof  LlLocationVar){

                    String stringLable = assemblyBuilder.generateStringLabel( );
                    assemblyBuilder.addLabel(stringLable);
                    assemblyBuilder.addLine(".string " + table.getFromStringTable(component));
                    assemblyBuilder.putOnStringTable(table.getMethodName()+"_"+(((LlLocationVar) component).getVarName()), stringLable);
                }

            }
        }

        for(LlBuilder builder : buildersList.getBuilders()){
            CFG cfg = new CFG(builder);

            HashSet<LlLocation> globalVArs = program.getGlobalVariables();

            if(options.contains("reg")){

                for (String reg : registers){
                    givenRegisters.add(reg);
                }

            }
            else{
                for (String reg : empty){
                    givenRegisters.add(reg);
                }
            }
            if(options.contains("cse")){

                GlobalCSE.performGlobalCommonSubexpressionEliminationOnCFG(cfg, globalVArs);
            }

            if(options.contains("cp")){

                GlobalCP.performGlobalCP(cfg, globalVArs);
            }

            if(options.contains("dce")){

                GlobalDCE.performGlobalDeadCodeElimination(cfg);
            }

            if(options.contains("ure")){

                GlobalURE.performGlobalURE(cfg);
            }

            if(options.contains("as")){

                AlgebraicSimplifications.performAlgebraicSimplifications(cfg);
            }
            if(options.contains("par")){
                (new Parallelize(program)).getProgram();
            }

            if(options.contains("all")){
                for (String reg : registers){
                    givenRegisters.add(reg);
                }

                GlobalCSE.performGlobalCommonSubexpressionEliminationOnCFG(cfg, globalVArs);
                GlobalCP.performGlobalCP(cfg, globalVArs);
                GlobalURE.performGlobalURE(cfg);
            }


            LlBuilder reordered = cfg.reorderLables();

            RegisterAllocation registerAllocation = new RegisterAllocation(givenRegisters, cfg);
            HashMap<LlLocation, String> varRegAllocs = registerAllocation.getVarRegisterAllocations();

            assemblyBuilder.updateRegisterAllocationTable(varRegAllocs);

            LlSymbolTable oldSymbolTable = buildersList.getSymbolTables().get(countSymbolTables++);
            LlSymbolTable symbolTable = new LlSymbolTable(builder.getName());

            this.addGlobalContextToLocalTables(buildersList, symbolTable);
            this.pushArraysToSymbolTable(oldSymbolTable, symbolTable);

            String currentMethodName = "";
            assemblyBuilder.addLine("");
            StackFrame frame = new StackFrame();;

            currentMethodName = builder.getName();


            if(!currentMethodName.equals("")) {

                assemblyBuilder.addLabel(currentMethodName);


                assemblyBuilder.addLinef(" .cfi_startproc", "");
                assemblyBuilder.addLinef("pushq ", "%rbp");
                assemblyBuilder.addLinef(".cfi_def_cfa_offset", "16");
                assemblyBuilder.addLinef(".cfi_offset", "6, -16");
                assemblyBuilder.addLinef("movq", "%rsp, %rbp");
                assemblyBuilder.addLine();
                assemblyBuilder.setEnterLine(currentMethodName);

                assemblyBuilder.calleeSave(assemblyBuilder.getAllAllocatedRegs(), frame);

                pushParamsToSymbolTable(assemblyBuilder, currentMethodName, methodParams, symbolTable, frame);
            }
            int statementCounter = 0;
            for(String label  : reordered.getStatementTable().keySet())
            {

                if(reordered.getStatementTable().get(label) instanceof  LlEmptyStmt && !label.equals(currentMethodName)){

                    assemblyBuilder.addLabel(symbolTable.getMethodName()+"_"+label);
                }
                if(statementCounter == reordered.getStatementTable().size()-1){
                    assemblyBuilder.isLastReturn = true;
                }
                reordered.getStatementTable().get(label).generateCode(assemblyBuilder, frame, symbolTable);
                statementCounter++;
            }

            assemblyBuilder.replaceEnterLine(currentMethodName, frame.getStackSize());

            if(assemblyBuilder.hasReturned){
                assemblyBuilder.hasReturned = false;
                assemblyBuilder.isLastReturn = false;
            }
            else{
                assemblyBuilder.calleeRestore(assemblyBuilder.getAllAllocatedRegs(), frame);
                assemblyBuilder.addLinef("leave","");
                assemblyBuilder.addLinef(".cfi_def_cfa","7, 8");
                assemblyBuilder.addLinef("ret", "");
                assemblyBuilder.addLinef(".cfi_endproc", "");
                assemblyBuilder.addLine();
                assemblyBuilder.hasReturned = false;
                assemblyBuilder.isLastReturn = false;
            }


        }
        assembled += assemblyBuilder.assemble();

        return assembled;
    }

    // basically loads the params on the stackFrame so that they can be accessed later.
    private void pushParamsToSymbolTable(AssemblyBuilder builder, String methodName, HashMap<String, ArrayList<LlLocationVar>> paramsTable, LlSymbolTable symbolTable, StackFrame frame){
        // generate stack locations on the params
        String paramRegs[] = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
        ArrayList<LlLocationVar> locations = paramsTable.get(methodName);
        for(int i = 0; i < locations.size(); i++){
          // put it in the stack
            if(builder.getAllocatedReg(locations.get(i))!=null){
                if(i<6){
                    String storageLoc = paramRegs[i];
                    builder.addLinef("movq", storageLoc + ", " + builder.getAllocatedReg(locations.get(i)));
                }
                else{
                    String storageLoc =  Integer.toString(16 + (i-6)*8);
                    builder.addLinef("movq", storageLoc + "(%rbp)" + ", " + builder.getAllocatedReg(locations.get(i)));
                }
            }
            else{
                String storageLoc;
                String stackLoc = frame.getNextStackLocation();

                if(i<6){

                    storageLoc = paramRegs[i];
                    builder.addLinef("movq", storageLoc + ", " + stackLoc);
                    symbolTable.putOnParamTable(locations.get(i), stackLoc);

                }
                else{
                    storageLoc =  Integer.toString(16 + (i-6)*8);
                    builder.addLinef("movq", storageLoc + "(%rbp)" + ", %r10");
                    builder.addLinef("movq", "%r10, " + stackLoc);
                    symbolTable.putOnParamTable(locations.get(i), stackLoc);
                }
                frame.pushToRegisterStackFrame("%r10");
            }

        }
    }
    // add the array names to the symbol table
    public void pushArraysToSymbolTable(LlSymbolTable oldSymbolTable, LlSymbolTable newSymbolTable){
        for(LlLocationVar key : oldSymbolTable.getArrayTable().keySet()){
            newSymbolTable.putOnArrayTable(key, oldSymbolTable.getFromArrayTable(key));
        }
    }
    // adds code for global vars and arrays at the top
    public void appendGlobalVarsAndArrays(AssemblyBuilder builder, LlBuildersList buildersList){
        for(LlLocationVar locationVar : buildersList.getGlobalArrays().keySet()){
            int arraySize = buildersList.getGlobalArrays().get(locationVar);
            builder.addLinef(".comm", locationVar.getVarName() + ", " + Integer.toString(arraySize*8) +", 64"); // might be 64
        }
        for(LlLocationVar locationVar : buildersList.getGlobalVars()){

            builder.addLinef(".comm", locationVar.getVarName() + ", 8, 8");
        }

    }

    public void addGlobalContextToLocalTables(LlBuildersList buildersList, LlSymbolTable localTable){
        for(LlLocationVar locationVar : buildersList.getGlobalArrays().keySet()){
            localTable.addToGlobalArrays(locationVar, buildersList.getGlobalArrays().get(locationVar) );
        }
        for(LlLocationVar locationVar : buildersList.getGlobalVars()){
            localTable.addToGlobalVars(locationVar);

        }
    }

}
