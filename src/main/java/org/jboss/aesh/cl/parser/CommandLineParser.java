/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.jboss.aesh.cl.parser;

import org.jboss.aesh.cl.CommandLine;
import org.jboss.aesh.cl.exception.ArgumentParserException;
import org.jboss.aesh.cl.exception.CommandLineParserException;
import org.jboss.aesh.cl.exception.OptionParserException;
import org.jboss.aesh.cl.exception.RequiredOptionException;
import org.jboss.aesh.cl.internal.OptionType;
import org.jboss.aesh.cl.internal.ProcessedCommand;
import org.jboss.aesh.cl.internal.ProcessedOption;
import org.jboss.aesh.cl.validator.OptionValidatorException;
import org.jboss.aesh.parser.AeshLine;
import org.jboss.aesh.parser.Parser;
import org.jboss.aesh.parser.ParserStatus;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * A simple command line parser.
 * It parses a given string based on the Command given and
 * returns a {@link org.jboss.aesh.cl.CommandLine}
 *
 * It can also print a formatted usage/help information.
 *
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class CommandLineParser {

    private ProcessedCommand command;
    private static final String EQUALS = "=";

    public CommandLineParser(ProcessedCommand command) {
        this.command = command;
    }

    public CommandLineParser(String name, String usage) {
        command = new ProcessedCommand(name, usage);
    }

    public ProcessedCommand getCommand() {
        return command;
    }

    /**
     * Returns a usage String based on the defined command and options.
     * Useful when printing "help" info etc.
     *
     */
    public String printHelp() {
        return command.printHelp();
    }

    /**
     * Parse a command line with the defined command as base of the rules.
     * If any options are found, but not defined in the command object an
     * CommandLineParserException will be thrown.
     * Also, if a required option is not found or options specified with value,
     * but is not given any value an OptionParserException will be thrown.
     *
     * The options found will be returned as a {@link org.jboss.aesh.cl.CommandLine} object where
     * they can be queried after.
     *
     * @param line input
     * @return CommandLine
     */
    public CommandLine parse(String line) {
        return parse(line, false);
    }

    /**
     * Parse a command line with the defined command as base of the rules.
     * If any options are found, but not defined in the command object an
     * CommandLineParserException will be thrown.
     * Also, if a required option is not found or options specified with value,
     * but is not given any value an CommandLineParserException will be thrown.
     *
     * The options found will be returned as a {@link CommandLine} object where
     * they can be queried after.
     *
     * @param line input
     * @param ignoreMissingRequirements if we should ignore
     * @return CommandLine
     * @throws CommandLineParserException
     */
    public CommandLine parse(String line, boolean ignoreMissingRequirements) {
        AeshLine aeshLine = Parser.findAllWords(line);
        if(aeshLine.getWords().size() > 0) {
            if(command.getName().equals(aeshLine.getWords().get(0)))
                return doParse(aeshLine.getWords(), ignoreMissingRequirements);
        }
        else if(aeshLine.getStatus() != ParserStatus.OK)
            return new CommandLine(new CommandLineParserException(aeshLine.getErrorMessage()));

        return new CommandLine(new CommandLineParserException("Command:"+ command +", not found in: "+line));
    }

    /**
     * Parse a command line with the defined command as base of the rules.
     * This method is useful when parsing a command line program thats not
     * in aesh, but rather a standalone command that want to parse input
     * parameters.
     *
     * @param lines input
     * @param ignoreMissing if we should ignore
     * @return CommandLine
     */
    public CommandLine doParse(List<String> lines, boolean ignoreMissing) {
        command.clear();
        CommandLine commandLine = new CommandLine();
        if(command.hasArgument())
            commandLine.setArgument(command.getArgument());
        ProcessedOption active = null;
        boolean addedArgument = false;
            //skip first entry since that's the name of the command
            for(int i=1; i < lines.size(); i++) {
                String parseLine = lines.get(i);
                //name
                if(parseLine.startsWith("--")) {
                    //make sure that we dont have any "active" options lying around
                    if(active != null &&
                            (active.getOptionType() != OptionType.LIST ||
                                    active.getOptionType() != OptionType.GROUP)) {
                        commandLine.setParserException(new OptionParserException("Option: "+active.getDisplayName()+" must be given a value"));
                        break;
                    }
                    else if(active != null) {
                        commandLine.addOption(active);
                        active = null;
                    }


                    active = findLongOption(command, parseLine.substring(2));
                    if(active != null)
                        active.setLongNameUsed(true);
                    if(active != null && active.isProperty()) {
                        if(parseLine.length() <= (2+active.getName().length()) ||
                                !parseLine.contains(EQUALS))
                            commandLine.setParserException(new OptionParserException(
                                    "Option "+active.getDisplayName()+", must be part of a property"));
                        else {
                            String name =
                                    parseLine.substring(2+active.getName().length(),
                                            parseLine.indexOf(EQUALS));
                            String value = parseLine.substring( parseLine.indexOf(EQUALS)+1);
                            if(value.length() < 1)
                                commandLine.setParserException(new OptionParserException("Option "+active.getDisplayName()+", must have a value"));
                            else {
                                active.addProperty(name, value);
                                commandLine.addOption(active);
                                active = null;
                                if(addedArgument)
                                    commandLine.setParserException(new ArgumentParserException("An argument was given to an option that do not support it."));
                            }
                        }
                    }
                    else if(active != null && (!active.hasValue() || active.getValue() != null)) {
                        active.addValue("true");
                        commandLine.addOption(active);
                        active = null;
                        if(addedArgument)
                            commandLine.setParserException(new ArgumentParserException("An argument was given to an option that do not support it."));
                    }
                    else if(active == null)
                        commandLine.setParserException(new OptionParserException("Option: "+parseLine+" is not a valid option for this command"));
                }
                //name
                else if(parseLine.startsWith("-")) {
                    //make sure that we dont have any "active" options lying around
                    //except list and group
                    if(active != null &&
                            (active.getOptionType() != OptionType.LIST ||
                                    active.getOptionType() != OptionType.GROUP)) {
                        commandLine.setParserException(new OptionParserException("Option: "+active.getDisplayName()+" must be given a value"));
                    }
                    else if(active != null) {
                        commandLine.addOption(active);
                        active = null;
                    }
                    else if(parseLine.length() != 2 && !parseLine.contains("=")) {
                        //we might have two or more options in a group
                        //if so, we only allow options (boolean) without value
                        if(parseLine.length() > 2) {
                            for(char shortName : parseLine.substring(1).toCharArray()) {
                                active = findOption(command, String.valueOf(shortName));
                                if(active != null) {
                                    if(!active.hasValue()) {
                                        active.setLongNameUsed(false);
                                        active.addValue("true");
                                        commandLine.addOption(active);
                                    }
                                    else
                                        commandLine.setParserException( new OptionParserException("Option: -"+shortName+
                                                        " can not be grouped with other options since it need to be given a value"));
                                }
                                else
                                    commandLine.setParserException(new OptionParserException("Option: -"+shortName+" was not found."));
                            }
                            //make sure to reset active
                            active = null;
                        }
                        else
                            commandLine.setParserException(new OptionParserException("Option: - must be followed by a valid operator"));
                    }
                    else {
                        active = findOption(command, parseLine.substring(1));
                        if(active != null)
                            active.setLongNameUsed(false);

                        if(active != null && active.isProperty()) {
                            if(parseLine.length() <= 2 ||
                                    !parseLine.contains(EQUALS))
                                commandLine.setParserException(new OptionParserException(
                                        "Option "+active.getDisplayName()+", must be part of a property"));
                            else {
                                String name =
                                        parseLine.substring(2, // 2+char.length
                                                parseLine.indexOf(EQUALS));
                                String value = parseLine.substring( parseLine.indexOf(EQUALS)+1);
                                if(value.length() < 1)
                                    commandLine.setParserException( new OptionParserException("Option "+active.getDisplayName()+", must have a value"));
                                else {
                                    active.addProperty(name, value);
                                    commandLine.addOption(active);
                                    active = null;
                                    if(addedArgument)
                                        commandLine.setParserException( new OptionParserException("An argument was given to an option that do not support it."));
                                }
                            }
                        }

                        else if(active != null && (!active.hasValue() || active.getValue() != null)) {
                            active.addValue("true");
                            commandLine.addOption(active);
                            active = null;
                            if(addedArgument)
                                commandLine.setParserException(new OptionParserException("An argument was given to an option that do not support it."));
                        }
                        else if(active == null)
                            commandLine.setParserException(new OptionParserException("Option: "+parseLine+" is not a valid option for this command"));
                    }
                }
                else if(active != null) {
                    if(active.hasMultipleValues()) {
                        if(parseLine.contains(String.valueOf(active.getValueSeparator()))) {
                            for(String value : parseLine.split(String.valueOf(active.getValueSeparator()))) {
                                active.addValue(value.trim());
                            }
                            commandLine.addOption(active);
                            active = null;
                        }
                        else
                            active.addValue(parseLine.trim());
                    }
                    else
                        active.addValue(parseLine);

                    if(active != null &&
                            (active.getOptionType() == OptionType.NORMAL ||
                            active.getOptionType() == OptionType.BOOLEAN)) {
                        commandLine.addOption(active);
                        active = null;
                    }
                    if(addedArgument)
                        commandLine.setParserException(new OptionParserException("An argument was given to an option that do not support it."));
                }
                //if no command is "active", we add it as an argument
                else {
                    if(command.getArgument() == null) {
                        commandLine.setParserException(new OptionParserException("An argument was given to a command that do not support it."));
                    }
                    else {
                        commandLine.addArgumentValue(parseLine);
                        addedArgument = true;
                    }
                }
            }

        if(active != null && (ignoreMissing ||
                (active.getOptionType() == OptionType.LIST || active.getOptionType() == OptionType.GROUP))) {
            commandLine.addOption(active);
        }

        //this will throw and CommandLineParserException if needed
        if(!ignoreMissing) {
            RequiredOptionException re = checkForMissingRequiredOptions(command, commandLine);
            if(re != null)
                commandLine.setParserException(re);
        }

        return commandLine;
    }

    private RequiredOptionException checkForMissingRequiredOptions(ProcessedCommand command, CommandLine commandLine) {
        for(ProcessedOption o : command.getOptions())
            if(o.isRequired()) {
                boolean found = false;
                for(ProcessedOption po : commandLine.getOptions()) {
                    if(po.getShortName().equals(o.getShortName()) ||
                            po.getShortName().equals(o.getName()))
                        found = true;
                }
                if(!found)
                    return new RequiredOptionException("Option: "+o.getDisplayName()+" is required for this command.");
            }
        return null;
    }

    private ProcessedOption findOption(ProcessedCommand command, String line) {
        ProcessedOption option = command.findOption(line);
        //simplest case
        if(option != null)
            return option;

        option = command.startWithOption(line);
        //if its a property, we'll parse it later
        if(option != null && option.isProperty())
            return option;
        if(option != null) {
           String rest = line.substring(option.getShortName().length());
            if(rest != null && rest.length() > 1 && rest.startsWith("=")) {
                option.addValue(rest.substring(1));
                return option;
            }
        }

        return null;
    }

    private ProcessedOption findLongOption(ProcessedCommand command, String line) {
        ProcessedOption option = command.findLongOption(line);
        //simplest case
        if(option != null)
            return option;

        option = command.startWithLongOption(line);
        //if its a property, we'll parse it later
        if(option != null && option.isProperty())
            return option;
        if(option != null) {
            String rest = line.substring(option.getName().length());
            if(rest != null && rest.length() > 1 && rest.startsWith("=")) {
                option.addValue(rest.substring(1));
                return option;
            }
        }

        return null;
    }

    /**
     * Populate a Command instance with the values parsed from a command line
     * If any parser errors are detected it will throw an exception
     * @param instance command
     * @param line command line
     * @throws CommandLineParserException
     */
    public void populateObject(Object instance, String line)
            throws CommandLineParserException, OptionValidatorException {
        populateObject(instance, line, true);
    }

    /**
     * Populate a Command instance with the values parsed from a command line
     * If any parser errors are detected it will throw an exception
     * @param instance command
     * @param line command line
     * @param validate do validation or not
     * @throws CommandLineParserException
     */
    public void populateObject(Object instance, String line, boolean validate)
            throws CommandLineParserException, OptionValidatorException {
        CommandLine cl = parse(line);
        if(cl.hasParserError())
            throw cl.getParserException();
        for(ProcessedOption option: command.getOptions()) {
            if(cl.hasOption(option.getName()))
                cl.getOption(option.getName()).injectValueIntoField(instance, validate);
            else if(option.getDefaultValues().size() > 0) {
                option.injectValueIntoField(instance, validate);
            }
            else
                resetField(instance, option.getFieldName(), option.hasValue());
        }
        if(cl.getArgument() != null && cl.getArgument().getValues().size() > 0) {
            cl.getArgument().injectValueIntoField(instance, validate);
        }
        else if(cl.getArgument() != null)
            resetField(instance, cl.getArgument().getFieldName(), true);
    }

    /**
     * Will parse the input line and populate the fields in the instance object specified by
     * the given annotations.
     * The instance object must be annotated with the CommandDefinition annotation @see CommandDefinition
     * Any parser errors will throw an exception
     *
     * @param instance command
     * @param line command line
     * @throws CommandLineParserException
     */
    public static void parseAndPopulate(Object instance, String line)
            throws CommandLineParserException, OptionValidatorException {
        ParserGenerator.generateCommandLineParser(instance.getClass()).populateObject(instance, line);
    }

    private void resetField(Object instance, String fieldName, boolean hasValue) {
        try {
            Field field = instance.getClass().getDeclaredField(fieldName);
            if(!Modifier.isPublic(field.getModifiers()))
                field.setAccessible(true);
            if(field.getType().isPrimitive()) {
                if(boolean.class.isAssignableFrom(field.getType()))
                    field.set(instance, false);
                else if(int.class.isAssignableFrom(field.getType()))
                    field.set(instance, 0);
                else if(short.class.isAssignableFrom(field.getType()))
                    field.set(instance, 0);
                else if(char.class.isAssignableFrom(field.getType()))
                    field.set(instance, '\u0000');
                else if(byte.class.isAssignableFrom(field.getType()))
                    field.set(instance, 0);
                else if(long.class.isAssignableFrom(field.getType()))
                    field.set(instance, 0L);
                else if(float.class.isAssignableFrom(field.getType()))
                    field.set(instance, 0.0f);
                else if(double.class.isAssignableFrom(field.getType()))
                    field.set(instance, 0.0d);
            }
            else if(!hasValue && field.getType().equals(Boolean.class)) {
               field.set(instance, Boolean.FALSE);
            }
            else
                field.set(instance, null);
        }
        catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return "CommandLineParser{" +
                "command=" + command +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommandLineParser)) return false;

        CommandLineParser that = (CommandLineParser) o;

        if (!command.equals(that.command)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return command.hashCode();
    }
}
