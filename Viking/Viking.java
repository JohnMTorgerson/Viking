package com.sillysoft.lux.agent;

import com.sillysoft.lux.*;
import com.sillysoft.lux.util.*;
import java.util.*;

//
//  Viking.java
//
//  Viking is written by John Torgerson and Jesse Halvorsen
//


public class Viking implements LuxAgent
{
    // This agent's ownerCode:
    protected int ID;
    
    // Store some refs the board and to the country array
    protected Board board;
    protected Country[] countries;
    
    // It might be useful to have a random number generator
    protected Random rand;
    
    public Viking()
    {
        rand = new Random();
    }
    
    // Save references
    public void setPrefs( int newID, Board theboard )
    {
        ID = newID;		// this is how we distinguish what countries we own
        
        board = theboard;
        countries = board.getCountries();
    }
    
    public String name()
    {
        return "Viking";
    }
    
    public float version()
    {
        return 1.0f;
    }
    
    public String description()
    {
        return "Viking is a bot";
    }
    
    protected void testChat(String topic, String message) {
//        if (topic == "continentFitness") { board.sendChat(message); }
        
    }
    
    // pick initial countries at the beginning of the game. We will do this later.
    public int pickCountry()
    {
        return -1;
    }
    
    // place initial armies at the beginning of the game
    public void placeInitialArmies( int numberOfArmies )
    {
        int[] bestContList = rateContinents(); // get ordered list of best continents to pursue
        int goalCont = bestContList[0]; // eventually we'll be more sophisticated about this
        
        board.sendChat(board.getContinentName(goalCont));
        
        int[] ownedInGoalCont = getPlayerCountriesInContinent(ID, goalCont, countries);
        for (int i = 0; i < ownedInGoalCont.length; i++) {
            board.sendChat(countries[ownedInGoalCont[i]].getName() + "\n");
        }
    }
    
    public void cardsPhase( Card[] cards )
    {
    }
    
    public void placeArmies( int numberOfArmies )
    {
    }
    
    public void attackPhase()
    {
    }
    
    public int moveArmiesIn( int cca, int ccd)
    {
        return 0;
    }
    
    public void fortifyPhase()
    {
    }
    
    // called when we win the game
    public String youWon()
    { 
        // For variety we store a bunch of answers and pick one at random to return.
        String[] answers = new String[] {
            "I won",
            "beees?!"
        };
        
        return answers[ rand.nextInt(answers.length) ];
    }
    
    public String message( String message, Object data )
    {
        return null;
    }
    
    /*
     *   ********* HELPER / CUSTOM FUNCTIONS *********
     */
    
    // helper function to rate continents to pursue based on several factors,
    // such as size, bonus, number of countries we own, etc...
    protected int[] rateContinents() {
        
        int goalCont = -1; // the continent we will choose
        float bestFitness = 0; // the continent with the best fitness so far
        float fitness; // the fitness of the current continent in each loop
        int numConts = BoardHelper.numberOfContinents(countries); // number of continents
        // declare the factors for each continent from which we'll calculate the fitness
        float bonus, numCountriesOwned, numArmiesOwned, numCountries, numEnemyArmies, numBorders;
        
        Map fitnessMap = new HashMap(); // key value array to store continent ID along with its fitness
        int[] results = new int[numConts]; // array of continent ID's, which will later be sorted by fitness
        String message = "";
        String name;
        
        // loop through all the continents and calculate their fitness, picking the highest one
        for(int cont = 0; cont < numConts; cont++) {
            
            // get the factors for the continent to calculate the fitness
            bonus = board.getContinentBonus(cont); // bonus
            numCountriesOwned = getPlayerCountriesInContinent(ID, cont, countries).length; // how many countries we own in cont
            numArmiesOwned = BoardHelper.getPlayerArmiesInContinent(ID, cont, countries); // how many of our armies in cont
            numCountries = BoardHelper.getContinentSize(cont, countries); // how many countries in cont
            numEnemyArmies = BoardHelper.getEnemyArmiesInContinent(ID, cont, countries); // how many enemy armies in cont
            numBorders = getSmartContinentBorders(cont, countries).length; // how many border countries
            name = board.getContinentName(cont);
            
            // calculate fitness
            fitness = (bonus * (numCountriesOwned + 1) * (numArmiesOwned + 1)) / ((float) Math.pow(numCountries,1.3) * (float) Math.pow(numBorders,2) * (numEnemyArmies + 1));
            
            fitnessMap.put(cont,fitness); // store fitness and ID as a key value pair in the map
            results[cont] = cont; // store continent ID's in this array, will get sorted later
            
            message = message + "\nname: " + name + " ID: " + cont + " fitness: " + fitnessMap.get(cont);
        }
        
        testChat("continentFitness",message);
        
        // bubble sort the results array by fitness
        boolean flag = true;
        int temp;
        float v1, v2;
        while(flag) {
            flag = false;
            for (int i=0; i<results.length-1; i++) {
                v1 = (Float)fitnessMap.get(results[i]);
                v2 = (Float)fitnessMap.get(results[i+1]);
                if (v1 < v2) {
                    temp = results[i];
                    results[i] = results[i+1];
                    results[i+1] = temp;
                    flag = true;
                }
            }
        }
        
        testChat("continentFitness",Arrays.toString(results));
        
        return results;
    }
    
    // helper function to return an array of the countries a player owns in a given continent
    protected int[] getPlayerCountriesInContinent(int ID, int cont, Country[] countries) {
        // continent iterator returns all countries in 'cont',
        // player iterator returns all countries out of those owned by 'ID'
        // this gives us the list of all the countries we own in the continent
        CountryIterator theCountries = new PlayerIterator(ID, new ContinentIterator(cont, countries));
        
        // Put all the countries we own into an ArrayList
        ArrayList countryArray = new ArrayList();
        while (theCountries.hasNext()) {
            countryArray.add(theCountries.next());
        }
        
        // Put the country code of each of the countries into an integer array
        int size = countryArray.size();
        int[] intArray = new int[size];
        for(int i=0; i < size; i++) {
            intArray[i] = ((Country)countryArray.get(i)).getCode();
        }
        
        return intArray;
    }
    
    // custom get continent borders function
    protected int[] getSmartContinentBorders(int cont, Country[] countries) {
        // eventually this function will pick borders of the continent that may include countries outside of the continent itself such that the number of borders to defend is smaller.
        // For now, it will just call the regular BoardHelper function to get the actual borders
        return BoardHelper.getContinentBorders(cont, countries);
    }
    
}
