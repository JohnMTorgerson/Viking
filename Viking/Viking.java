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
        if (topic == "placeInitialArmies") { board.sendChat(message); }
        if (topic == "getAreaTakeoverPath") { board.sendChat(message); }

    }
    
    // pick initial countries at the beginning of the game. We will do this later.
    public int pickCountry()
    {
        return -1;
    }
    
    // place initial armies at the beginning of the game
    public void placeInitialArmies( int numberOfArmies )
    {
        // first we'll decide what continents to pursue and thus which ones we'll place armies on
        int[] bestContList = rateContinents(); // get ordered list of best continents to pursue
        int goalCont = bestContList[0]; // pick the best one from that list (eventually we'll be more sophisticated about this)
        
        testChat("placeInitialArmies","goalCont: " + board.getContinentName(goalCont));
        
        // next we need to pick a country to place our armies on in order to take over the continent
        // the getContTakeoverPath function will simulate taking over the continent
        // from multiple countries and give us back the best path to do so
        // the first country in that path list is the one we want to place our armies on
        int[] goalContCountries = getCountriesInContinent(goalCont, countries); // put all the countries from the goal cont into an integer array to pass to the getAreaTakeoverPath function
        testChat("placeInitialArmies", "countries in goalCont: " + Arrays.toString(goalContCountries));
        int[] contTakeoverPath = getAreaTakeoverPath(goalContCountries);
        
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
    
    // will return a path of attack from a country (optionally supplied by passing startCountry)
    // to take over as many countries as possible in the given countryList (countryList may be a continent, for example, but doesn't have to be)
    // if startCountry is not provided, will test multiple starting countries in the countryList and choose the best one
    // if we don't own any countries in the countryList, we'll find one nearby to start on
    protected int[] getAreaTakeoverPath(int[] countryList, int startCountry) {
        ArrayList paths = new ArrayList(); // we'll store all candidate paths here (individual paths are integer arrays)
        
        if (startCountry != -1) { // a startCountry was supplied, so find all paths starting from that country only
            testChat("getAreaTakeoverPath", "startCountry is " + startCountry);
            // create an new history (integer array) containing only the starting country
            int[] temp = new int[1];
            temp[0] = startCountry;
            // find paths
            paths = findAreaPaths(temp, countryList);
        }
        else { // no startCountry was supplied, so pick our own candidates
            testChat("getAreaTakeoverPath", "startCountry not given");
            int[] candidates = getPlayerCountriesInArea(ID, countryList, countries); // get countries in countryList that we own

            // just testing to see if we found the countries we own
            String message = "";
            String name;
            for (int i=0; i < candidates.length; i++) {
                name = countries[candidates[i]].getName();
                message = message + name + ", ";
            }
            testChat("getAreaTakeoverPath", "countries we own in goalCont: " + message);
            
            // loop through candidates array, finding paths for each of them
            // concatenate results from all of them together in the paths ArrayList
            
            if (candidates.length == 0) { // we don't own any countries in countryList
                // find the country outside of countryList that we own with the cheapest path to it
                // use that as starting country
            }
        }
        
        // test all candidate paths and pick the best one
        
        // return the best path
        return new int[] {0,0,0,0};
    }
    // overload getAreaTakeoverPath to allow a single parameter version
    protected int[] getAreaTakeoverPath(int[] countryList) {
        return getAreaTakeoverPath(countryList, -1);
    }
    
    // find all possible paths through enemy countries within countryList
    // starting with the last country in the history array
    // history is an array of country codes containing the path history already searched
    // countryList is an array of country codes in which the search takes place
    // this may typically be a continent, but doesn't have to be
    // returns an ArrayList of paths (which are integer arrays)
    // the function is called recursively
    protected ArrayList findAreaPaths(int[] history, int[] countryList) {
        return new ArrayList();
    }
    
    // function to rate continents to pursue based on several factors,
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
    
    // helper function to return an array of the countries a player owns in a given list of countries (area)
    protected int[] getPlayerCountriesInArea(int ID, int[] area, Country[] countries) {
        // loop through all the countries in area; if ID owns them, add them to the results ArrayList
        List<Integer> results = new ArrayList<Integer>();
        for (int i=0; i<area.length; i++) {
            if (countries[area[i]].getOwner() == ID) {
                results.add(area[i]);
            }
        }
        
        // Put the country code of each of the countries into an integer array
        int size = results.size();
        int[] intArray = new int[size];
        for(int i=0; i < size; i++) {
            intArray[i] = results.get(i).intValue();
        }
        
        return intArray;
    }

    // helper function to return an array of the countries in a given continent
    protected int[] getCountriesInContinent(int cont, Country[] countries) {
        // continent iterator returns all countries in 'cont'
        CountryIterator theCountries = new ContinentIterator(cont, countries);
        
        // Put all the countries into an ArrayList
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
